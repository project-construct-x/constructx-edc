# Basic Abac Extension (draft version)

This extension allows you to create policy definitions that enable attribute-based access control.

That means you have to specify a credential type by setting a `leftOperand` that designates a fully qualified credential type identifier. The identifier must be followed by `.credentialSubject` and one or more JSONPath segments that allow navigating through the credential subject used in that credential type. Please note that this extension will (at least currently) only work as expected, if you are using constraints within 
the `permission` of a policy definition. 

Consider the following example:

```json
{
  "permission": [
    {
      "action": "use",
      "constraint": [
        {
          "and": [
            {
              "leftOperand": "https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember",
              "operator": "eq",
              "rightOperand": "true"
            },
            {
              "leftOperand": "https://w3id.org/constructx/credentials/v1.0/Baustelle123Credential.credentialSubject.accessLevel",
              "operator": "gteq",
              "rightOperand": 4
            }
          ]
        }
      ]
    }
  ]
}
```

Here, the first `leftOperand` is:

```text
https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember
```

The last path segment consists of:

1. The complete credential type, in this case `ConstructXMembershipCredential`
2. The mandatory `.credentialSubject` segment
3. One or more JSONPath segments, in this case `.isMember`

### Pattern matching

The `leftOperand` of a policy definition must match the regex pattern currently defined in the [BasicAbacUtils](./src/main/java/de/fraunhofer/isst/edc/extension/basic_abac/dev/BasicAbacUtils.java) class:

```java
public static final String BASIC_ABAC_REGEX =
    "^https://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?::[0-9]+)?(?:/[^\\s/]+)*/[A-Z][^\\s/.]*\\.credentialSubject(?:\\.[^\\s/.]+)+$";
```

In essence, this regex matches if the target string:

- is a valid HTTPS URL,
- has a last path segment that starts with a capital letter,
- contains `.credentialSubject` directly after the complete credential type, and
- contains one or more JSONPath segments after `.credentialSubject`.

The JSONPath segments following .credentialSubject specify the path to a value within the credential subject of the corresponding verifiable credential.

#### Examples matching the regex

- `https://w3id.org/constructx/credentials/v1.0/Foo.credentialSubject.fooLevel`
- `https://w3id.org/constructx/credentials/v1.0/Bar.credentialSubject.nestedObject.barLevel`
- `https://my-domain.com:8080/some/arbitrary/path/segments/Foo.credentialSubject.fooLevel`
- `https://w3id.org/constructx/policies/v1.0/Foo.credentialSubject.fooLevel`

#### Examples not matching the regex

- `https://w3id.org/constructx/credentials/v1.0/Foo`  
  Missing `.credentialSubject` and a JSONPath segment.

- `https://w3id.org/constructx/credentials/v1.0/Foo.credentialSubject`  
  Missing a JSONPath segment after `.credentialSubject`.

- `https://w3id.org/constructx/credentials/v1.0/Foo.fooLevel`  
  Missing the mandatory `.credentialSubject` segment.

- `http://example.org/credentials/Foo.credentialSubject.fooLevel`  
  Uses HTTP instead of HTTPS.

- `https://w3id.org/constructx/credentials/v1.0/moo.credentialSubject.mooLevel`  
  The last path segment does not start with a capital letter.

#### Understanding the JSONPath suffix

To create sensible ABAC policies, you need to be aware of the structure of the credential subject in the verifiable credential.

Consider the following `leftOperand`:

```text
https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember
```

The corresponding credential subject could be structured as follows:

```json
{
  "id": "did:web:consumer-wallet:user:consumer",
  "isMember": "true"
}
```

The mandatory `.credentialSubject` segment identifies the credential subject. The following JSONPath segment `.isMember` points to the value of the `isMember` field, which is the string `"true"` in this example. Just b.t.w.: Note that a JSON literal `true` would have also worked here (more on that below). 

Since the policy definition expects that boolean value to be equal to `true`, this condition is fulfilled.

Now consider the second ABAC policy condition:

```text
https://w3id.org/constructx/credentials/v1.0/Baustelle123Credential.credentialSubject.accessLevel
```

Assume the consumer participant has this credential, but the trusted issuer provided it with the following credential subject:

```json
{
  "id": "did:web:consumer-wallet:user:consumer",
  "accessLevel": "3"
}
```

The evaluation determines that an `accessLevel` of `4` is required, while only level `3` was granted. Therefore, the consumer participant is denied access when attempting to negotiate the corresponding contract offer.

#### Equality of values

This extension chooses to be rather generous when it comes to comparing different variants of representations of numeric or boolean values. 

That means for example

- a string containing five, i.e. `"5"` is equal to a numeric `5`. And both are equal to a decimal `5.0`. 
- a string whose content sort of looks like a boolean, e.g. `"true"`, `"FaLSE"` or `"TRUE"` will be interpreted as the corresponding boolean values `true` or `false` respectively. I.e. there is no case-sensitivity here. 

#### Default Credential

There is a technical necessity that the controlplane minimally needs at least one credential type it will expect or show to external partners. This is especially relevant when there is no credential-specific policy context, from which any credential types could possibly get extracted, i.e. when someone is trying to request someone else's EDC catalog. It can be defined with a property:  

```
edc.abac.defaultcredential=https://w3id.org/my/very/special/FooMembershipCredential
```

You are not required to use that property. If you don't, it will default to `https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential`.

