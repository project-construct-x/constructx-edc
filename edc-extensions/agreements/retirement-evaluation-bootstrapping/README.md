## RetirementEvaluationBootstrappingExtension

This extension will utilize the SqlSchemaBootstrapper in order to make sure that the SQL table `edc_agreement_retirement` gets created. 

This is because the retirement-evaluation-store-sql extension does not take care of the creation of this table itself, but instead relies on the controlplane-migration extension. 

So this extension is useful as a lightweight-replacement, if you want to use the agreement-retirement extension with SQL persistence, but don't want to use the database migration extension. 

