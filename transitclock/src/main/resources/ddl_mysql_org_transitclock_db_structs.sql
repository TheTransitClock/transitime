
    create table ActiveRevisions (
        id integer not null auto_increment,
        configRev integer,
        travelTimesRev integer,
        primary key (id)
    );

    create table Agencies (
        configRev integer not null,
        agencyName varchar(60) not null,
        agencyId varchar(60) not null,
        agencyFareUrl varchar(255),
        agencyLang varchar(15),
        agencyPhone varchar(15),
        agencyTimezone varchar(40),
        agencyUrl varchar(255),
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        primary key (configRev, agencyName, agencyId)
    );

    create table ArrivalsDepartures (
        DTYPE varchar(31) not null,
        vehicleId varchar(60) not null,
        tripId varchar(60) not null,
        time datetime(3) not null,
        stopId varchar(60) not null,
        isArrival bit not null,
        gtfsStopSeq integer not null,
        avlTime datetime(3),
        blockId varchar(60),
        configRev integer,
        directionId varchar(60),
        freqStartTime datetime(3),
        routeId varchar(60),
        routeShortName varchar(60),
        scheduledTime datetime(3),
        serviceId varchar(60),
        stopOrder integer,
        stopPathIndex integer,
        stopPathLength float,
        tripIndex integer,
        primary key (vehicleId, tripId, time, stopId, isArrival, gtfsStopSeq)
    );

    create table AvlReports (
        vehicleId varchar(60) not null,
        time datetime(3) not null,
        assignmentId varchar(60),
        assignmentType varchar(40),
        driverId varchar(60),
        field1Name varchar(60),
        field1Value varchar(60),
        heading float,
        licensePlate varchar(10),
        lat double precision,
        lon double precision,
        passengerCount integer,
        passengerFullness float,
        source varchar(10),
        speed float,
        timeProcessed datetime(3),
        primary key (vehicleId, time)
    );

    create table Block_to_Trip_joinTable (
        Blocks_serviceId varchar(60) not null,
        Blocks_configRev integer not null,
        Blocks_blockId varchar(60) not null,
        trips_tripId varchar(60) not null,
        trips_startTime integer not null,
        trips_configRev integer not null,
        listIndex integer not null,
        primary key (Blocks_serviceId, Blocks_configRev, Blocks_blockId, listIndex)
    );

    create table Blocks (
        serviceId varchar(60) not null,
        configRev integer not null,
        blockId varchar(60) not null,
        endTime integer,
        routeIds blob,
        startTime integer,
        primary key (serviceId, configRev, blockId)
    );

    create table CalendarDates (
        serviceId varchar(60) not null,
        date date not null,
        configRev integer not null,
        exceptionType varchar(2),
        primary key (serviceId, date, configRev)
    );

    create table Calendars (
        wednesday bit not null,
        tuesday bit not null,
        thursday bit not null,
        sunday bit not null,
        startDate date not null,
        serviceId varchar(60) not null,
        saturday bit not null,
        monday bit not null,
        friday bit not null,
        endDate date not null,
        configRev integer not null,
        primary key (wednesday, tuesday, thursday, sunday, startDate, serviceId, saturday, monday, friday, endDate, configRev)
    );

    create table ConfigRevision (
        configRev integer not null,
        notes longtext,
        processedTime datetime(3),
        zipFileLastModifiedTime datetime(3),
        primary key (configRev)
    );

    create table DbTest (
        id integer not null,
        primary key (id)
    );

    create table FareAttributes (
        fareId varchar(60) not null,
        configRev integer not null,
        currencyType varchar(3),
        paymentMethod varchar(255),
        price float,
        transferDuration integer,
        transfers varchar(255),
        primary key (fareId, configRev)
    );

    create table FareRules (
        routeId varchar(60) not null,
        originId varchar(60) not null,
        fareId varchar(60) not null,
        destinationId varchar(60) not null,
        containsId varchar(60) not null,
        configRev integer not null,
        primary key (routeId, originId, fareId, destinationId, containsId, configRev)
    );

    create table Frequencies (
        tripId varchar(60) not null,
        startTime integer not null,
        configRev integer not null,
        endTime integer,
        exactTimes bit,
        headwaySecs integer,
        primary key (tripId, startTime, configRev)
    );

    create table Headway (
        id bigint not null auto_increment,
        average double precision,
        coefficientOfVariation double precision,
        configRev integer,
        creationTime datetime(3),
        firstDeparture datetime(3),
        headway double precision,
        numVehicles integer,
        otherVehicleId varchar(60),
        routeId varchar(60),
        secondDeparture datetime(3),
        stopId varchar(60),
        tripId varchar(60),
        variance double precision,
        vehicleId varchar(60),
        primary key (id)
    );

    create table HoldingTimes (
        id bigint not null auto_increment,
        arrivalPredictionUsed bit,
        arrivalTime datetime(3),
        arrivalUsed bit,
        configRev integer,
        creationTime datetime(3),
        hasD1 bit,
        holdingTime datetime(3),
        numberPredictionsUsed integer,
        routeId varchar(60),
        stopId varchar(60),
        tripId varchar(60),
        vehicleId varchar(60),
        primary key (id)
    );

    create table Matches (
        vehicleId varchar(60) not null,
        avlTime datetime(3) not null,
        atStop bit,
        blockId varchar(60),
        configRev integer,
        distanceAlongSegment float,
        distanceAlongStopPath float,
        segmentIndex integer,
        serviceId varchar(255),
        stopPathIndex integer,
        tripId varchar(60),
        primary key (vehicleId, avlTime)
    );

    create table MeasuredArrivalTimes (
        time datetime(3) not null,
        stopId varchar(60) not null,
        directionId varchar(60),
        headsign varchar(60),
        routeId varchar(60),
        routeShortName varchar(60),
        primary key (time, stopId)
    );

    create table MonitoringEvents (
        type varchar(40) not null,
        time datetime(3) not null,
        message longtext,
        triggered bit,
        value double precision,
        primary key (type, time)
    );

    create table PredictionAccuracy (
        id bigint not null auto_increment,
        affectedByWaitStop bit,
        arrivalDepartureTime datetime(3),
        directionId varchar(60),
        predictedTime datetime(3),
        predictionAccuracyMsecs integer,
        predictionReadTime datetime(3),
        predictionSource varchar(60),
        routeId varchar(60),
        routeShortName varchar(60),
        stopId varchar(60),
        tripId varchar(60),
        vehicleId varchar(60),
        primary key (id)
    );

    create table Predictions (
        id bigint not null auto_increment,
        affectedByWaitStop bit,
        avlTime datetime(3),
        configRev integer,
        creationTime datetime(3),
        gtfsStopSeq integer,
        isArrival bit,
        predictionTime datetime(3),
        routeId varchar(60),
        schedBasedPred bit,
        stopId varchar(60),
        tripId varchar(60),
        vehicleId varchar(60),
        primary key (id)
    );

    create table Routes (
        id varchar(60) not null,
        configRev integer not null,
        color varchar(10),
        description longtext,
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        hidden bit,
        longName varchar(255),
        maxDistance double precision,
        name varchar(255),
        routeOrder integer,
        shortName varchar(255),
        textColor varchar(10),
        type varchar(2),
        primary key (id, configRev)
    );

    create table StopPathPredictions (
        id bigint not null auto_increment,
        algorithm varchar(255),
        creationTime datetime(3),
        predictionTime double precision,
        startTime integer,
        stopPathIndex integer,
        travelTime bit,
        tripId varchar(60),
        vehicleId varchar(255),
        primary key (id)
    );

    create table StopPath_locations (
        StopPath_tripPatternId varchar(120) not null,
        StopPath_stopPathId varchar(120) not null,
        StopPath_configRev integer not null,
        lat double precision,
        lon double precision,
        locations_ORDER integer not null,
        primary key (StopPath_tripPatternId, StopPath_stopPathId, StopPath_configRev, locations_ORDER)
    );

    create table StopPaths (
        tripPatternId varchar(120) not null,
        stopPathId varchar(120) not null,
        configRev integer not null,
        breakTime integer,
        gtfsStopSeq integer,
        lastStopInTrip bit,
        layoverStop bit,
        maxDistance double precision,
        maxSpeed double precision,
        pathLength double precision,
        routeId varchar(60),
        scheduleAdherenceStop bit,
        stopId varchar(60),
        waitStop bit,
        primary key (tripPatternId, stopPathId, configRev)
    );

    create table Stops (
        id varchar(60) not null,
        configRev integer not null,
        code integer,
        hidden bit,
        layoverStop bit,
        lat double precision,
        lon double precision,
        name varchar(255),
        timepointStop bit,
        waitStop bit,
        primary key (id, configRev)
    );

    create table Transfers (
        toStopId varchar(60) not null,
        fromStopId varchar(60) not null,
        configRev integer not null,
        minTransferTime integer,
        transferType varchar(1),
        primary key (toStopId, fromStopId, configRev)
    );

    create table TravelTimesForStopPaths (
        id integer not null auto_increment,
        configRev integer,
        daysOfWeekOverride smallint,
        howSet varchar(5),
        stopPathId varchar(120),
        stopTimeMsec integer,
        travelTimeSegmentLength float,
        travelTimesMsec mediumblob,
        travelTimesRev integer,
        primary key (id)
    );

    create table TravelTimesForTrip_to_TravelTimesForPath_joinTable (
        TravelTimesForTrips_id integer not null,
        travelTimesForStopPaths_id integer not null,
        listIndex integer not null,
        primary key (TravelTimesForTrips_id, listIndex)
    );

    create table TravelTimesForTrips (
        id integer not null auto_increment,
        configRev integer,
        travelTimesRev integer,
        tripCreatedForId varchar(60),
        tripPatternId varchar(120),
        primary key (id)
    );

    create table TripPattern_to_Path_joinTable (
        TripPatterns_id varchar(120) not null,
        TripPatterns_configRev integer not null,
        stopPaths_tripPatternId varchar(120) not null,
        stopPaths_stopPathId varchar(120) not null,
        stopPaths_configRev integer not null,
        listIndex integer not null,
        primary key (TripPatterns_id, TripPatterns_configRev, listIndex)
    );

    create table TripPatterns (
        id varchar(120) not null,
        configRev integer not null,
        directionId varchar(60),
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        headsign varchar(255),
        routeId varchar(60),
        routeShortName varchar(80),
        shapeId varchar(60),
        primary key (id, configRev)
    );

    create table Trip_scheduledTimesList (
        Trip_tripId varchar(60) not null,
        Trip_startTime integer not null,
        Trip_configRev integer not null,
        arrivalTime integer,
        departureTime integer,
        scheduledTimesList_ORDER integer not null,
        primary key (Trip_tripId, Trip_startTime, Trip_configRev, scheduledTimesList_ORDER)
    );

    create table Trips (
        tripId varchar(60) not null,
        startTime integer not null,
        configRev integer not null,
        blockId varchar(60),
        directionId varchar(60),
        endTime integer,
        exactTimesHeadway bit,
        headsign varchar(255),
        noSchedule bit,
        routeId varchar(60),
        routeShortName varchar(60),
        serviceId varchar(60),
        shapeId varchar(60),
        tripShortName varchar(60),
        travelTimes_id integer,
        tripPattern_id varchar(120),
        tripPattern_configRev integer,
        primary key (tripId, startTime, configRev)
    );

    create table VehicleConfigs (
        id varchar(60) not null,
        capacity integer,
        crushCapacity integer,
        description varchar(255),
        nonPassengerVehicle bit,
        trackerId varchar(60),
        type integer,
        primary key (id)
    );

    create table VehicleEvents (
        vehicleId varchar(60) not null,
        time datetime(3) not null,
        eventType varchar(60) not null,
        avlTime datetime(3),
        becameUnpredictable bit,
        blockId varchar(60),
        description longtext,
        lat double precision,
        lon double precision,
        predictable bit,
        routeId varchar(60),
        routeShortName varchar(60),
        serviceId varchar(60),
        stopId varchar(60),
        supervisor varchar(60),
        tripId varchar(60),
        primary key (vehicleId, time, eventType)
    );

    create table VehicleStates (
        vehicleId varchar(60) not null,
        avlTime datetime(3) not null,
        blockId varchar(60),
        isDelayed bit,
        isForSchedBasedPreds bit,
        isLayover bit,
        isPredictable bit,
        isWaitStop bit,
        routeId varchar(60),
        routeShortName varchar(80),
        schedAdh varchar(50),
        schedAdhMsec integer,
        schedAdhWithinBounds bit,
        tripId varchar(60),
        tripShortName varchar(60),
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
        references Trips (tripId, startTime, configRev);

    alter table Block_to_Trip_joinTable 
        add constraint FK_1c1e1twdap19vq0xkav0amvm 
        foreign key (Blocks_serviceId, Blocks_configRev, Blocks_blockId) 
        references Blocks (serviceId, configRev, blockId);

    alter table StopPath_locations 
        add constraint FK_sdjt3vtd3w0cl07p0doob6khi 
        foreign key (StopPath_tripPatternId, StopPath_stopPathId, StopPath_configRev) 
        references StopPaths (tripPatternId, stopPathId, configRev);

    alter table TravelTimesForTrip_to_TravelTimesForPath_joinTable 
        add constraint FK_hh5uepurijcqj0pyc6e3h5mqw 
        foreign key (travelTimesForStopPaths_id) 
        references TravelTimesForStopPaths (id);

    alter table TravelTimesForTrip_to_TravelTimesForPath_joinTable 
        add constraint FK_9j1s8ewsmokqg4m35wrr29na7 
        foreign key (TravelTimesForTrips_id) 
        references TravelTimesForTrips (id);

    alter table TripPattern_to_Path_joinTable 
        add constraint FK_s0gaw8iv60vc17a5ltryqwg27 
        foreign key (stopPaths_tripPatternId, stopPaths_stopPathId, stopPaths_configRev) 
        references StopPaths (tripPatternId, stopPathId, configRev);

    alter table TripPattern_to_Path_joinTable 
        add constraint FK_qsr8l6u1nelb5pt8rlnei08sy 
        foreign key (TripPatterns_id, TripPatterns_configRev) 
        references TripPatterns (id, configRev);

    alter table Trip_scheduledTimesList 
        add constraint FK_n5et0p70cwe1dwo4m6lq0k4h0 
        foreign key (Trip_tripId, Trip_startTime, Trip_configRev) 
        references Trips (tripId, startTime, configRev);

    alter table Trips 
        add constraint FK_p1er53449kkfsca6mbnxkdyst 
        foreign key (travelTimes_id) 
        references TravelTimesForTrips (id);

    alter table Trips 
        add constraint FK_676npp7h4bxh8sjcnugnxt5wb 
        foreign key (tripPattern_id, tripPattern_configRev) 
        references TripPatterns (id, configRev);
