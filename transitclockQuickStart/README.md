How to set up transiTime QuickStart:

to build the program, you can do this with the command

```shell
mvn install -DskipTests
```

This will take a few minutes to run. when complete navigate to 
transitimeQuickStart
from here you see the `target/transitimeQuickStart.jar` to run it from here enter the line:

```shell
java -jar -Dtransitclock.configFiles=src/main/resources/transitclock.properties target/transitclockQuickStart.jar
```

The gui should then pop up if done correctly.

You should see a GUI pop up with multiple fields where you can enter your data. Please note that transitimeQuickStart will use default values if nothing entered.

If you wish to use your own gtfs file and realtime feed please enter it correctly into the fields. If you wish to simply see transiTime running leave these fields empty. Choose to start web app if you wish to see transiTime realtime map data and predictions(recommended)

Click next, a progress bar will appear below. may take several minutes to start up.

When completed you should see an output screen with values you can enter into the onebusaway quickStart. Otherwise, you can open the web app your default browser (the bottom "open in browser" button) From here you should see transiTime running in your default browser.

To see map data click maps-->map for a selected route--> on map screen select in top left corner you can select a route you wish to see


