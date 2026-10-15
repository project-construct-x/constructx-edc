# con-x-migration extension

This extension is heavily inspired by Tractus-X's [connector-migration](../connector-migration) extension for the controlplane. 
However, since Construct-X is a younger project, this extension does not need to cover any database table changes in the 
EDC that happened before the beginning of the year 2026. 

Upcoming changes in the future can be handled via adding new sql-files (with increasing version number) in the [migrations.constructx](src/main/resources/migrations/constructx) folder.  

## Usage

This extension executes automatically at boot-time of the controlplane. You just have to make sure that the generally required 
database properties are configured: 

 - edc.datasource.default.url
 - edc.datasource.default.user
 - edc.datasource.default.password