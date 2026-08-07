# Basic Abac Extension (draft version)

This extension allows you to create policy definitions that enable attribute-based access control. 

That means, you have to specify a credential type by setting a leftOperand that designates a fully qualified credential 
type identifier, and that needs to be appended by a "pointer" that allows navigating through the structure of the credential 
subject that is used in that credential type.  

Consider the following example: 

```
          "permission": [
            {
              "action": "use",
              "constraint": [
                {
                  "and": [
                    {
                      "leftOperand": "https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.isMember",
                      "operator": "eq",
                      "rightOperand": "true"
                    },
                    {
                      "leftOperand": "https://w3id.org/constructx/credentials/v1.0/Baustelle123Credential.accessLevel",
                      "operator": "gteq",
                      "rightOperand": "4"
                    }
                  ]
                }
              ]
            }
          ]
```

Here, we have "https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.isMember" first. The last 
path segment must have a "pointer" suffix (see more on that below). In this case, the suffix would be  ".isMember"


### Pattern matching

If the leftOperand of a policy definition matches a certain regex pattern (current defined in the [BasicAbacUtils](./src/main/java/de/fraunhofer/isst/edc/extension/basic_abac/dev/BasicAbacUtils.java) - class). 

```java
    public static final String BASIC_ABAC_REGEX =
        "^(?=.*/credentials)https://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?::[0-9]+)?/.*/[A-Z][^\\s/.]*(?:\\.[^\\s/.]+)+$";
```

In essence, this regex matches if the target string 
- is a valid https-url and
- that has the "magic word" "/credentials" somewhere in its path segments and
- whose last path segment starts with a capital letter and 
- the suffix of the last path segment must provide a "pointer" to a value in the credential subject
  of the corresponding verifiable credential.

The suffix of the last path segment can and should be used to provide a "pointer" to a value in the credential subject 
of the corresponding verifiable credential.

#### Examples (matching the regex)

- "https://w3id.org/constructx/credentials/v1.0/Foo.fooLevel" 
- "https://w3id.org/constructx/credentials/v1.0/Bar.nestedObject.barLevel"
- "https://my-domain.com:8080/credentials/Foo.fooLevel"


#### Examples (NOT matching the regex)

- "https://w3id.org/constructx/credentials/v1.0/Foo" (missing "pointer" suffix)
- "http://example.org/credentials/Foo.fooLevel" (http is not https)
- "https://w3id.org/constructx/credentials/v1.0/moo.mooLevel" (no capital letter at start of last path segment)
- "https://w3id.org/constructx/policies/v1.0/Foo.fooLevel" (missing "/credentials" magic word) 

#### Understanding the "pointer suffix"

In order to create sensible ABAC policies, you need to be aware of the given structure of the credential subject in the 
verifiable credential. Continuing the above-mentioned example, let's suppose that the credential subject of the "https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential" is structured like this: 

```
          {
            "id": "did:web:consumer-wallet:user:consumer",
            "isMember": "true"
          }
```

Then, the given suffix (".isMember") is pointing at value of the "isMember" field that JSON object, i.e. the string "true". 
Since our policy definition (see above again) is expecting the value "true" to be equal to whatever is found in the value 
of the credential subject, everything is fine here. 

Now let's expand this further to the second ABAC policy condition. Here, we see "https://w3id.org/constructx/credentials/v1.0/Baustelle123Credential.accessLevel". Assuming that our consumer-participant 
does have this credential, but the relevant trusted issuer gave it to him with this credential subject ...  

```
          {
            "id": "did:web:consumer-wallet:user:consumer",
            "accessLevel": "3"
          }
```

... then the evaluation would find that an accessLevel of 4 was required, but only level 3 was granted to him. Which means, 
that this consumer participant is out of luck here, and he will be denied if he attempts to negotiate for this contract offer. 
