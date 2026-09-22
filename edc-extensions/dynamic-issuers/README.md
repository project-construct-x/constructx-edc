## Dynamic trusted issuers extension

This extension enables you to let your controlplane fetch information about trusted issuers. In order for this to work, 
your dataspace organisation needs to have set up a trust server. In essence, this extension provides an alternative 
implementation of a TrustedIssuerRegistry, which replaces the DefaultTrustedIssuerRegistry. 

You can provide the trust server's URL via an environment property like in this example: 

```
edc.iam.trustserver.url=https://my-trustserver.com/trusted-issuers
```

You can omit this property entirely. In that case, no attempts to fetch updates will be made and this extension will 
simply behave like the DefaultTrustedIssuerRegistry from the upstream EDC.

Also note, that this extension does in general not interfere with the TrustedIssuerConfigurationExtension. I.e. you can 
still define trusted issuers purely via your env-properties. Conflicts can only arise, when an issuer-did:web-id, which 
you provided via property, is also known to the remote trusted issuer server. In that case, the information from the 
remote server will take precedence and override anything conflicting that may have been given in your properties. 

The rationale is, that the remote server (which is administered by the dataspace governance organization) is expected to have more recent information. 

### The information payload

The response body from the trust server is expected to be in this format: 

```json
{
  "interval": 30,
  "issuerdata": [
    {
      "id": "did:web:local-issuer-wallet:con-x-issuer",
      "supportedTypes": [
        "https://my-domain.com/credentials/essential/MembershipCredential",
        "https://my-domain.com/credentials/other/FooCredential"
      ]
    }
  ]
}
```

At first, the body may contain an "interval" field. This allows the remote server to tell this extension, after how many 
seconds a new update request should be made. The idea is, that the central trust server may have load-balancing-related 
reasons to lower the intensity of incoming requests from his multiple clients. This field is however optional. If the trust
server chooses not to use this field, then the default interval (usually 2 hours) will be applied. 

Beyond that, the response body must always have an "issuerdata" field, that supplies an array of objects. Each of these 
objects must have an "id" field, that states the did:web-id of one particular issuer. And it needs to have a "supportedTypes" field with an array value attached. This array must contain strings of Credential-Types, for which this issuer is authorized to create credentials. 

Note that an asterisk symbol ("*") in this array would be interpreted as a wildcard. I.e. an issuer-id that is equipped with 
such a wild card is assumed to be allowed to create ANY CredentialType, so caution is advised here. 

### Configuring the default update interval

You can override the above-mentioned default interval using this property: 

```
edc.iam.trustserver.default.interval=3600
```
The given value sets the amount of seconds, i.e. 3600 seconds would equal one hour. Please note that this value will only 
ever be used if the remote server chooses not to use to "interval" field in his response (see above). 

Aside from that, a special initialization interval of 30 seconds will be used on bootup, as long as your controlplane has not 
managed to establish a connection to the trust server at least once. 
