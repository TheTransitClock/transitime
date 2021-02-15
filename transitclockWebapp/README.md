This is the web application for TheTransitClock and can be built by 

```
cd transitclockWebapp
maven install -DskipTests
```

This produces a war file for deployment web.war in the target directory.

You will need to configure the location of the transitclockConfig.xml file as a command line argument:

`-Dtransitclock.configFiles=/path/to/your/transitclockConfig.xml`

```
#example script
export CATALINA_OPTS="-Dtransitclock.apikey=8a3273b0 \
-Dtransitclock.configFiles=/usr/local/transitclock/config/02.xml
-Dtransitclock.hibernate.configFile=/usr/local/transitclock/config/hibernate_cfg.xml"

export JAVA_OPTS="-Dtransitclock.apikey=8a3273b0 \
-Dtransitclock.configFiles=/usr/local/transitclock/config/02.xml
-Dtransitclock.hibernate.configFile=/usr/local/transitclock/config/hibernate_cfg.xml"
```
If you have multiple agencies in your system, only the primary agency which has in its table structure the access data for the other instances should have its configuration file given as command line parameter. Both the Web UI and the API can function in a multi-tenancy model in this way. If you are running on Tomcat, specifying the command line options once is enough for both the API and Web endpoints.

The exact place to do this depends on how you're running TheTransitClock. In Eclipse, add this as a VM argument in the run configuration for Tomcat. In a bash script, add it to `CATALINA_OPTS` before Tomcat starts up.

The transitclockConfig.xml file in turn is used to specify the location of the database and the hibernate file.

You will also need to configure the key for accessing the transitclockApi in the template/includes.jsp file. You can use the CreateAPIKey application in TheTransitClock to create a test/demo key. This you may already have done as part of the setup of transitclockApi.

You first need to have the core instances running, the RMI Registry running and the API setup before Web UI functions will become usable.