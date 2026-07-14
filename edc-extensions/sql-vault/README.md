## SqlVaultExtension

This extension is meant to be used as a light-weight alternative to the EDC's HashiCorp Vault extension. It will store 
all secrets, that the EDC reads from or writes to the vault, on your postgres database. For this purpose, a table named 
[sql_vault](./src/main/resources/sql-vault.sql) will be created.

I.e. if you want to use this extension, you also need to use the EDC's regular postgres related extensions. 

### Security Concerns

Please note, that this extension is intended as a compromise, that prioritizes simple usability over maximum security, 
since operating a HashiCorp vault in a professional and persistent way can be somewhat demanding. So please don't use this 
extension and stick to the HashiCorp vault extension, if you require maximum security. 


### Usage 
This extension will self-register at boot-time and then deploy initial secrets, which you can define via as described in the following. 

#### Secret definition via explicit property 

You can define any number of key-value pairs using this property ```edc.sql.store.vault.initdata```. Use ```;;;``` as 
a separator between two key-value pairs, and ```:::``` as separator between key and value. 

Example: 

```
edc.sql.store.vault.initdata=foo:::123;;;bar:::456
```

This will store the value ```123``` under the secret alias ```foo``` and ```456``` under the secret alias ```bar```. 


#### Secret definition via mounted directory 

Alternatively you can use this property ```edc.sql.store.vault.directory``` to define a directory which may contain files 
that shall be used for initialization of secrets. The names of the contained files are used as key, the content 
of the file is used as the secret's value. The file content is expected to be a UTF-encoded (i.e. a "normal" text content). 

Example: 

```
edc.sql.store.vault.directory=/app/vault-init
```

Please note that the given directory path must be valid from your EDC runtime's perspective. I.e. if you are running your 
EDC in a container, you may want to use a volume mount, which maps a directory on your host machine into the EDC container.

Example: 
```
    volumes:
      - ./testsecrets:/app/vault-init
```

You can omit the above-mentioned property, which will then default to ```/app/vault-init```. You are not obliged to
actually provide any directory here. You will be notified via logging output, if no directory was found.

Let's assume, that the mounted folder ```testsecrets``` contains these two files: 

```
.
├── docker-compose.yaml
├── [...]
└─── testsecrets
    ├── file-secret-one
    └── file-secret-two
```

Then, the extension will create two secrets (named ```file-secret-one``` and ```file-secret-two```) and read the content 
of each of these files as UTF-encoded text. 