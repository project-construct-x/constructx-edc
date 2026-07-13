# con-x-controlplane-postgresql-vault Module

This runtime is a variant of the con-x-controlplane-postgresql-hashicorp-vault runtime, which is using the
[SqlVaultExtension](../../../edc-extensions/sql-vault/README.md) instead of the EDC's HashiCorp Vault extension in order to manage the handling of certain 
secrets that the creators of the EDC considered important enough to be stored in a vault. Like for example cryptographic 
keys and things like that. 

Since handling and operating a Hashicorp vault in a professional way can be a somewhat demanding task, this runtime allows 
you to choose an alternative, which may not be optimal under pure security considerations, but in exchange makes operating 
a con-x EDC a bit less difficult.   

## Building

From the project root directory, run

```shell
./gradlew :edc-controlplane:edc-controlplane-construct-x:con-x-controlplane-postgres-vault:dockerize
```
