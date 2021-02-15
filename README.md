# CodeployAcademyWso2Handler

Il presente progetto è un handler wso2esb che permette di gestire le richiese in esb.

- clean package progetto
- deploy jar in repository/components/lib
- aggiungere al file repository/conf/synapse-handlers.xml la seguente configurazione

```<handlers>
     <handler name = "classname"class="package.classname"/>
</handlers>
```
 
- aggiungere al file <ESB_HOME>/repository/conf/log4j.properties la seguente configurazione

```log4j.logger.<package.classname>r=DEBUG```
