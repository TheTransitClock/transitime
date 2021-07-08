
    create table ActiveRevisions (
        id number(10,0) not null,
        configRev number(10,0),
        travelTimesRev number(10,0),
        primary key (id)
    );

    create table Agencies (
        configRev number(10,0) not null,
        agencyName varchar2(60 char) not null,
        agencyId varchar2(60 char) not null,
        agencyFareUrl varchar2(255 char),
        agencyLang varchar2(15 char),
        agencyPhone varchar2(15 char),
        agencyTimezone varchar2(40 char),
        agencyUrl varchar2(255 char),
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        primary key (configRev, agencyName, agencyId)
    );

    create table ArrivalsDepartures (
        DTYPE varchar2(31 char) not null,
        vehicleId varchar2(60 char) not null,
        tripId varchar2(60 char) not null,
        time timestamp not null,
        stopId varchar2(60 char) not null,
        isArrival number(1,0) not null,
        gtfsStopSeq number(10,0) not null,
        avlTime timestamp,
        blockId varchar2(60 char),
        configRev number(10,0),
        directionId varchar2(60 char),
        freqStartTime timestamp,
        routeId varchar2(60 char),
        routeShortName varchar2(60 char),
        scheduledTime timestamp,
        serviceId varchar2(60 char),
        stopOrder number(10,0),
        stopPathIndex number(10,0),
        stopPathLength float,
        tripIndex number(10,0),
        primary key (vehicleId, tripId, time, stopId, isArrival, gtfsStopSeq)
    );

    create table AvlReports (
        vehicleId varchar2(60 char) not null,
        time timestamp not null,
        assignmentId varchar2(60 char),
        assignmentType varchar2(40 char),
        driverId varchar2(60 char),
        field1Name varchar2(60 char),
        field1Value varchar2(60 char),
        heading float,
        licensePlate varchar2(10 char),
        lat double precision,
        lon double precision,
        passengerCount number(10,0),
        passengerFullness float,
        source varchar2(10 char),
        speed float,
        timeProcessed timestamp,
        primary key (vehicleId, time)
    );

    create table Block_to_Trip_joinTable (
        Blocks_serviceId varchar2(60 char) not null,
        Blocks_configRev number(10,0) not null,
        Blocks_blockId varchar2(60 char) not null,
        trips_tripId varchar2(60 char) not null,
        trips_startTime number(10,0) not null,
        trips_configRev number(10,0) not null,
        listIndex number(10,0) not null,
        primary key (Blocks_serviceId, Blocks_configRev, Blocks_blockId, listIndex)
    );

    create table Blocks (
        serviceId varchar2(60 char) not null,
        configRev number(10,0) not null,
        blockId varchar2(60 char) not null,
        endTime number(10,0),
        routeIds raw(500),
        startTime number(10,0),
        primary key (serviceId, configRev, blockId)
    );

    create table CalendarDates (
        serviceId varchar2(60 char) not null,
        date date not null,
        configRev number(10,0) not null,
        exceptionType varchar2(2 char),
        primary key (serviceId, date, configRev)
    );

    create table Calendars (
        wednesday number(1,0) not null,
        tuesday number(1,0) not null,
        thursday number(1,0) not null,
        sunday number(1,0) not null,
        startDate date not null,
        serviceId varchar2(60 char) not null,
        saturday number(1,0) not null,
        monday number(1,0) not null,
        friday number(1,0) not null,
        endDate date not null,
        configRev number(10,0) not null,
        primary key (wednesday, tuesday, thursday, sunday, startDate, serviceId, saturday, monday, friday, endDate, configRev)
    );

    create table ConfigRevision (
        configRev number(10,0) not null,
        notes varchar2(512 char),
        processedTime timestamp,
        zipFileLastModifiedTime timestamp,
        primary key (configRev)
    );

    create table DbTest (
        id number(10,0) not null,
        primary key (id)
    );

    create table FareAttributes (
        fareId varchar2(60 char) not null,
        configRev number(10,0) not null,
        currencyType varchar2(3 char),
        paymentMethod varchar2(255 char),
        price float,
        transferDuration number(10,0),
        transfers varchar2(255 char),
        primary key (fareId, configRev)
    );

    create table FareRules (
        routeId varchar2(60 char) not null,
        originId varchar2(60 char) not null,
        fareId varchar2(60 char) not null,
        destinationId varchar2(60 char) not null,
        containsId varchar2(60 char) not null,
        configRev number(10,0) not null,
        primary key (routeId, originId, fareId, destinationId, containsId, configRev)
    );

    create table Frequencies (
        tripId varchar2(60 char) not null,
        startTime number(10,0) not null,
        configRev number(10,0) not null,
        endTime number(10,0),
        exactTimes number(1,0),
        headwaySecs number(10,0),
        primary key (tripId, startTime, configRev)
    );

    create table Headway (
        id number(19,0) not null,
        average double precision,
        coefficientOfVariation double precision,
        configRev number(10,0),
        creationTime timestamp,
        firstDeparture timestamp,
        headway double precision,
        numVehicles number(10,0),
        otherVehicleId varchar2(60 char),
        routeId varchar2(60 char),
        secondDeparture timestamp,
        stopId varchar2(60 char),
        tripId varchar2(60 char),
        variance double precision,
        vehicleId varchar2(60 char),
        primary key (id)
    );

    create table HoldingTimes (
        id number(19,0) not null,
        arrivalPredictionUsed number(1,0),
        arrivalTime timestamp,
        arrivalUsed number(1,0),
        configRev number(10,0),
        creationTime timestamp,
        hasD1 number(1,0),
        holdingTime timestamp,
        numberPredictionsUsed number(10,0),
        routeId varchar2(60 char),
        stopId varchar2(60 char),
        tripId varchar2(60 char),
        vehicleId varchar2(60 char),
        primary key (id)
    );

    create table Matches (
        vehicleId varchar2(60 char) not null,
        avlTime timestamp not null,
        atStop number(1,0),
        blockId varchar2(60 char),
        configRev number(10,0),
        distanceAlongSegment float,
        distanceAlongStopPath float,
        segmentIndex number(10,0),
        serviceId varchar2(255 char),
        stopPathIndex number(10,0),
        tripId varchar2(60 char),
        primary key (vehicleId, avlTime)
    );

    create table MeasuredArrivalTimes (
        time timestamp not null,
        stopId varchar2(60 char) not null,
        directionId varchar2(60 char),
        headsign varchar2(60 char),
        routeId varchar2(60 char),
        routeShortName varchar2(60 char),
        primary key (time, stopId)
    );

    create table MonitoringEvents (
        type varchar2(40 char) not null,
        time timestamp not null,
        message varchar2(512 char),
        triggered number(1,0),
        value double precision,
        primary key (type, time)
    );

    create table PredictionAccuracy (
        id number(19,0) not null,
        affectedByWaitStop number(1,0),
        arrivalDepartureTime timestamp,
        directionId varchar2(60 char),
        predictedTime timestamp,
        predictionAccuracyMsecs number(10,0),
        predictionReadTime timestamp,
        predictionSource varchar2(60 char),
        routeId varchar2(60 char),
        routeShortName varchar2(60 char),
        stopId varchar2(60 char),
        tripId varchar2(60 char),
        vehicleId varchar2(60 char),
        primary key (id)
    );

    create table Predictions (
        id number(19,0) not null,
        affectedByWaitStop number(1,0),
        avlTime timestamp,
        configRev number(10,0),
        creationTime timestamp,
        gtfsStopSeq number(10,0),
        isArrival number(1,0),
        predictionTime timestamp,
        routeId varchar2(60 char),
        schedBasedPred number(1,0),
        stopId varchar2(60 char),
        tripId varchar2(60 char),
        vehicleId varchar2(60 char),
        primary key (id)
    );

    create table Routes (
        id varchar2(60 char) not null,
        configRev number(10,0) not null,
        color varchar2(10 char),
        description varchar2(1024 char),
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        hidden number(1,0),
        longName varchar2(255 char),
        maxDistance double precision,
        name varchar2(255 char),
        routeOrder number(10,0),
        shortName varchar2(255 char),
        textColor varchar2(10 char),
        type varchar2(2 char),
        primary key (id, configRev)
    );

    create table StopPathPredictions (
        id number(19,0) not null,
        algorithm varchar2(255 char),
        creationTime timestamp,
        predictionTime double precision,
        startTime number(10,0),
        stopPathIndex number(10,0),
        travelTime number(1,0),
        tripId varchar2(60 char),
        vehicleId varchar2(255 char),
        primary key (id)
    );

    create table StopPath_locations (
        StopPath_tripPatternId varchar2(120 char) not null,
        StopPath_stopPathId varchar2(120 char) not null,
        StopPath_configRev number(10,0) not null,
        lat double precision,
        lon double precision,
        locations_ORDER number(10,0) not null,
        primary key (StopPath_tripPatternId, StopPath_stopPathId, StopPath_configRev, locations_ORDER)
    );

    create table StopPaths (
        tripPatternId varchar2(120 char) not null,
        stopPathId varchar2(120 char) not null,
        configRev number(10,0) not null,
        breakTime number(10,0),
        gtfsStopSeq number(10,0),
        lastStopInTrip number(1,0),
        layoverStop number(1,0),
        maxDistance double precision,
        maxSpeed double precision,
        pathLength double precision,
        routeId varchar2(60 char),
        scheduleAdherenceStop number(1,0),
        stopId varchar2(60 char),
        waitStop number(1,0),
        primary key (tripPatternId, stopPathId, configRev)
    );

    create table Stops (
        id varchar2(60 char) not null,
        configRev number(10,0) not null,
        code number(10,0),
        hidden number(1,0),
        layoverStop number(1,0),
        lat double precision,
        lon double precision,
        name varchar2(255 char),
        timepointStop number(1,0),
        waitStop number(1,0),
        primary key (id, configRev)
    );

    create table Transfers (
        toStopId varchar2(60 char) not null,
        fromStopId varchar2(60 char) not null,
        configRev number(10,0) not null,
        minTransferTime number(10,0),
        transferType varchar2(1 char),
        primary key (toStopId, fromStopId, configRev)
    );

    create table TravelTimesForStopPaths (
        id number(10,0) not null,
        configRev number(10,0),
        daysOfWeekOverride number(5,0),
        howSet varchar2(5 char),
        stopPathId varchar2(120 char),
        stopTimeMsec number(10,0),
        travelTimeSegmentLength float,
        travelTimesMsec long raw,
        travelTimesRev number(10,0),
        primary key (id)
    );

    create table TravelTimesForTrip_to_TravelTimesForPath_joinTable (
        TravelTimesForTrips_id number(10,0) not null,
        travelTimesForStopPaths_id number(10,0) not null,
        listIndex number(10,0) not null,
        primary key (TravelTimesForTrips_id, listIndex)
    );

    create table TravelTimesForTrips (
        id number(10,0) not null,
        configRev number(10,0),
        travelTimesRev number(10,0),
        tripCreatedForId varchar2(60 char),
        tripPatternId varchar2(120 char),
        primary key (id)
    );

    create table TripPattern_to_Path_joinTable (
        TripPatterns_id varchar2(120 char) not null,
        TripPatterns_configRev number(10,0) not null,
        stopPaths_tripPatternId varchar2(120 char) not null,
        stopPaths_stopPathId varchar2(120 char) not null,
        stopPaths_configRev number(10,0) not null,
        listIndex number(10,0) not null,
        primary key (TripPatterns_id, TripPatterns_configRev, listIndex)
    );

    create table TripPatterns (
        id varchar2(120 char) not null,
        configRev number(10,0) not null,
        directionId varchar2(60 char),
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        headsign varchar2(255 char),
        routeId varchar2(60 char),
        routeShortName varchar2(80 char),
        shapeId varchar2(60 char),
        primary key (id, configRev)
    );

    create table Trip_scheduledTimesList (
        Trip_tripId varchar2(60 char) not null,
        Trip_startTime number(10,0) not null,
        Trip_configRev number(10,0) not null,
        arrivalTime number(10,0),
        departureTime number(10,0),
        scheduledTimesList_ORDER number(10,0) not null,
        primary key (Trip_tripId, Trip_startTime, Trip_configRev, scheduledTimesList_ORDER)
    );

    create table Trips (
        tripId varchar2(60 char) not null,
        startTime number(10,0) not null,
        configRev number(10,0) not null,
        blockId varchar2(60 char),
        directionId varchar2(60 char),
        endTime number(10,0),
        exactTimesHeadway number(1,0),
        headsign varchar2(255 char),
        noSchedule number(1,0),
        routeId varchar2(60 char),
        routeShortName varchar2(60 char),
        serviceId varchar2(60 char),
        shapeId varchar2(60 char),
        tripShortName varchar2(60 char),
        travelTimes_id number(10,0),
        tripPattern_id varchar2(120 char),
        tripPattern_configRev number(10,0),
        primary key (tripId, startTime, configRev)
    );

    create table VehicleConfigs (
        id varchar2(60 char) not null,
        capacity number(10,0),
        crushCapacity number(10,0),
        description varchar2(255 char),
        nonPassengerVehicle number(1,0),
        trackerId varchar2(60 char),
        type number(10,0),
        primary key (id)
    );

    create table VehicleEvents (
        vehicleId varchar2(60 char) not null,
        time timestamp not null,
        eventType varchar2(60 char) not null,
        avlTime timestamp,
        becameUnpredictable number(1,0),
        blockId varchar2(60 char),
        description varchar2(500 char),
        lat double precision,
        lon double precision,
        predictable number(1,0),
        routeId varchar2(60 char),
        routeShortName varchar2(60 char),
        serviceId varchar2(60 char),
        stopId varchar2(60 char),
        supervisor varchar2(60 char),
        tripId varchar2(60 char),
        primary key (vehicleId, time, eventType)
    );

    create table VehicleStates (
        vehicleId varchar2(60 char) not null,
        avlTime timestamp not null,
        blockId varchar2(60 char),
        isDelayed number(1,0),
        isForSchedBasedPreds number(1,0),
        isLayover number(1,0),
        isPredictable number(1,0),
        isWaitStop number(1,0),
        routeId varchar2(60 char),
        routeShortName varchar2(80 char),
        schedAdh varchar2(50 char),
        schedAdhMsec number(10,0),
        schedAdhWithinBounds number(1,0),
        tripId varchar2(60 char),
        tripShortName varchar2(60 char),
        primary key (vehicleId, avlTime)
    );

    create index ArrivalsDeparturesTimeIndex on ArrivalsDepartures (time);

    create index ArrivalsDeparturesRouteTimeIndex on ArrivalsDepartures (routeShortName, time);

    create index AvlReportsTimeIndex on AvlReports (time);

    create index HeadwayIndex on Headway (creationTime);

    create index HoldingTimeIndex on HoldingTimes (creationTime);

    create index AvlTimeIndex on Matches (avlTime);

    create index MeasuredArrivalTimesIndex on MeasuredArrivalTimes (time);

    create index MonitoringEventsTimeIndex on MonitoringEvents (time);

    create index PredictionAccuracyTimeIndex on PredictionAccuracy (arrivalDepartureTime);

    create index PredictionTimeIndex on Predictions (creationTime);

    create index StopPathPredictionTimeIndex on StopPathPredictions (tripId, stopPathIndex);

    create index TravelTimesRevIndex on TravelTimesForTrips (travelTimesRev);

    alter table TripPattern_to_Path_joinTable 
        add constraint UK_s0gaw8iv60vc17a5ltryqwg27  unique (stopPaths_tripPatternId, stopPaths_stopPathId, stopPaths_configRev);

    create index VehicleEventsTimeIndex on VehicleEvents (time);

    create index VehicleStateAvlTimeIndex on VehicleStates (avlTime);

    alter table Block_to_Trip_joinTable 
        add constraint FK_abaj8ke6oh4imbbgnaercsowo 
        foreign key (trips_tripId, trips_startTime, trips_configRev) 
        references Trips;

    alter table Block_to_Trip_joinTable 
        add constraint FK_1c1e1twdap19vq0xkav0amvm 
        foreign key (Blocks_serviceId, Blocks_configRev, Blocks_blockId) 
        references Blocks;

    alter table StopPath_locations 
        add constraint FK_sdjt3vtd3w0cl07p0doob6khi 
        foreign key (StopPath_tripPatternId, StopPath_stopPathId, StopPath_configRev) 
        references StopPaths;

    alter table TravelTimesForTrip_to_TravelTimesForPath_joinTable 
        add constraint FK_hh5uepurijcqj0pyc6e3h5mqw 
        foreign key (travelTimesForStopPaths_id) 
        references TravelTimesForStopPaths;

    alter table TravelTimesForTrip_to_TravelTimesForPath_joinTable 
        add constraint FK_9j1s8ewsmokqg4m35wrr29na7 
        foreign key (TravelTimesForTrips_id) 
        references TravelTimesForTrips;

    alter table TripPattern_to_Path_joinTable 
        add constraint FK_s0gaw8iv60vc17a5ltryqwg27 
        foreign key (stopPaths_tripPatternId, stopPaths_stopPathId, stopPaths_configRev) 
        references StopPaths;

    alter table TripPattern_to_Path_joinTable 
        add constraint FK_qsr8l6u1nelb5pt8rlnei08sy 
        foreign key (TripPatterns_id, TripPatterns_configRev) 
        references TripPatterns;

    alter table Trip_scheduledTimesList 
        add constraint FK_n5et0p70cwe1dwo4m6lq0k4h0 
        foreign key (Trip_tripId, Trip_startTime, Trip_configRev) 
        references Trips;

    alter table Trips 
        add constraint FK_p1er53449kkfsca6mbnxkdyst 
        foreign key (travelTimes_id) 
        references TravelTimesForTrips;

    alter table Trips 
        add constraint FK_676npp7h4bxh8sjcnugnxt5wb 
        foreign key (tripPattern_id, tripPattern_configRev) 
        references TripPatterns;

    create sequence hibernate_sequence;
