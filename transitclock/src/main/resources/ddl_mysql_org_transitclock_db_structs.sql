
    create table ActiveRevisions (
        id integer not null auto_increment,
        configRev integer,
        travelTimesRev integer,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table Agencies (
        configRev integer not null,
        agencyName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        agencyFareUrl varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        agencyId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        agencyLang varchar(15) CHARACTER SET utf8 COLLATE utf8_bin,
        agencyPhone varchar(15) CHARACTER SET utf8 COLLATE utf8_bin,
        agencyTimezone varchar(40) CHARACTER SET utf8 COLLATE utf8_bin,
        agencyUrl varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        primary key (configRev, agencyName)
    ) DEFAULT CHARSET=utf8;

    create table ArrivalsDepartures (
        DTYPE varchar(31) CHARACTER SET utf8 COLLATE utf8_bin not null,
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        time datetime(3) not null,
        stopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        isArrival bit not null,
        gtfsStopSeq integer not null,
        avlTime datetime(3),
        blockId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        configRev integer,
        directionId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        freqStartTime datetime(3),
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeShortName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        scheduledTime datetime(3),
        serviceId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        stopOrder integer,
        stopPathIndex integer,
        stopPathLength float,
        tripIndex integer,
        primary key (vehicleId, tripId, time, stopId, isArrival, gtfsStopSeq)
    ) DEFAULT CHARSET=utf8;

    create table AvlReports (
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        time datetime(3) not null,
        assignmentId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        assignmentType varchar(40) CHARACTER SET utf8 COLLATE utf8_bin,
        driverId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        field1Name varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        field1Value varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        heading float,
        licensePlate varchar(10) CHARACTER SET utf8 COLLATE utf8_bin,
        lat double precision,
        lon double precision,
        passengerCount integer,
        passengerFullness float,
        source varchar(10) CHARACTER SET utf8 COLLATE utf8_bin,
        speed float,
        timeProcessed datetime(3),
        primary key (vehicleId, time)
    ) DEFAULT CHARSET=utf8;

    create table Block_to_Trip_joinTable (
        Blocks_serviceId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        Blocks_configRev integer not null,
        Blocks_blockId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        trips_tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        trips_startTime integer not null,
        trips_configRev integer not null,
        listIndex integer not null,
        primary key (Blocks_serviceId, Blocks_configRev, Blocks_blockId, listIndex)
    ) DEFAULT CHARSET=utf8;

    create table Blocks (
        serviceId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        configRev integer not null,
        blockId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        endTime integer,
        routeIds blob,
        startTime integer,
        primary key (serviceId, configRev, blockId)
    ) DEFAULT CHARSET=utf8;

    create table CalendarDates (
        serviceId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        date date not null,
        configRev integer not null,
        exceptionType varchar(2),
        primary key (serviceId, date, configRev)
    ) DEFAULT CHARSET=utf8;

    create table Calendars (
        wednesday bit not null,
        tuesday bit not null,
        thursday bit not null,
        sunday bit not null,
        startDate date not null,
        serviceId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        saturday bit not null,
        monday bit not null,
        friday bit not null,
        endDate date not null,
        configRev integer not null,
        primary key (wednesday, tuesday, thursday, sunday, startDate, serviceId, saturday, monday, friday, endDate, configRev)
    ) DEFAULT CHARSET=utf8;

    create table ConfigRevision (
        configRev integer not null,
        notes longtext,
        processedTime datetime(3),
        zipFileLastModifiedTime datetime(3),
        primary key (configRev)
    ) DEFAULT CHARSET=utf8;

    create table DbTest (
        id integer not null,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table FareAttributes (
        fareId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        configRev integer not null,
        currencyType varchar(3),
        paymentMethod varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        price float,
        transferDuration integer,
        transfers varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (fareId, configRev)
    ) DEFAULT CHARSET=utf8;

    create table FareRules (
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        originId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        fareId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        destinationId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        containsId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        configRev integer not null,
        primary key (routeId, originId, fareId, destinationId, containsId, configRev)
    ) DEFAULT CHARSET=utf8;

    create table Frequencies (
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        startTime integer not null,
        configRev integer not null,
        endTime integer,
        exactTimes bit,
        headwaySecs integer,
        primary key (tripId, startTime, configRev)
    ) DEFAULT CHARSET=utf8;

    create table Headway (
        id bigint not null auto_increment,
        average double precision,
        coefficientOfVariation double precision,
        configRev integer,
        creationTime datetime(3),
        firstDeparture datetime(3),
        headway double precision,
        numVehicles integer,
        otherVehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        secondDeparture datetime(3),
        stopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        variance double precision,
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

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
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        stopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table Matches (
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        avlTime datetime(3) not null,
        atStop bit,
        blockId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        configRev integer,
        distanceAlongSegment float,
        distanceAlongStopPath float,
        segmentIndex integer,
        serviceId varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        stopPathIndex integer,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (vehicleId, avlTime)
    ) DEFAULT CHARSET=utf8;

    create table MeasuredArrivalTimes (
        time datetime(3) not null,
        stopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        directionId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        headsign varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeShortName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (time, stopId)
    ) DEFAULT CHARSET=utf8;

    create table MonitoringEvents (
        type varchar(40) CHARACTER SET utf8 COLLATE utf8_bin not null,
        time datetime(3) not null,
        message longtext,
        triggered bit,
        value double precision,
        primary key (type, time)
    ) DEFAULT CHARSET=utf8;

    create table PredictionAccuracy (
        id bigint not null auto_increment,
        affectedByWaitStop bit,
        arrivalDepartureTime datetime(3),
        directionId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        predictedTime datetime(3),
        predictionAccuracyMsecs integer,
        predictionReadTime datetime(3),
        predictionSource varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeShortName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        stopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table Predictions (
        id bigint not null auto_increment,
        affectedByWaitStop bit,
        avlTime datetime(3),
        configRev integer,
        creationTime datetime(3),
        gtfsStopSeq integer,
        isArrival bit,
        predictionTime datetime(3),
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        schedBasedPred bit,
        stopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table Routes (
        id varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        configRev integer not null,
        color varchar(10) CHARACTER SET utf8 COLLATE utf8_bin,
        description longtext,
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        hidden bit,
        longName varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        maxDistance double precision,
        name varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        routeOrder integer,
        shortName varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        textColor varchar(10) CHARACTER SET utf8 COLLATE utf8_bin,
        type varchar(2),
        primary key (id, configRev)
    ) DEFAULT CHARSET=utf8;

    create table StopPathPredictions (
        id bigint not null auto_increment,
        algorithm varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        creationTime datetime(3),
        predictionTime double precision,
        startTime integer,
        stopPathIndex integer,
        travelTime bit,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        vehicleId varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table StopPath_locations (
        StopPath_tripPatternId varchar(120) CHARACTER SET utf8 COLLATE utf8_bin not null,
        StopPath_stopPathId varchar(120) CHARACTER SET utf8 COLLATE utf8_bin not null,
        StopPath_configRev integer not null,
        lat double precision,
        lon double precision,
        locations_ORDER integer not null,
        primary key (StopPath_tripPatternId, StopPath_stopPathId, StopPath_configRev, locations_ORDER)
    ) DEFAULT CHARSET=utf8;

    create table StopPaths (
        tripPatternId varchar(120) CHARACTER SET utf8 COLLATE utf8_bin not null,
        stopPathId varchar(120) CHARACTER SET utf8 COLLATE utf8_bin not null,
        configRev integer not null,
        breakTime integer,
        gtfsStopSeq integer,
        lastStopInTrip bit,
        layoverStop bit,
        maxDistance double precision,
        maxSpeed double precision,
        pathLength double precision,
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        scheduleAdherenceStop bit,
        stopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        waitStop bit,
        primary key (tripPatternId, stopPathId, configRev)
    ) DEFAULT CHARSET=utf8;

    create table Stops (
        id varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        configRev integer not null,
        code integer,
        hidden bit,
        layoverStop bit,
        lat double precision,
        lon double precision,
        name varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        timepointStop bit,
        waitStop bit,
        primary key (id, configRev)
    ) DEFAULT CHARSET=utf8;

    create table Transfers (
        toStopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        fromStopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        configRev integer not null,
        minTransferTime integer,
        transferType varchar(1),
        primary key (toStopId, fromStopId, configRev)
    ) DEFAULT CHARSET=utf8;

    create table TravelTimesForStopPaths (
        id integer not null auto_increment,
        configRev integer,
        daysOfWeekOverride smallint,
        howSet varchar(5),
        stopPathId varchar(120) CHARACTER SET utf8 COLLATE utf8_bin,
        stopTimeMsec integer,
        travelTimeSegmentLength float,
        travelTimesMsec mediumblob,
        travelTimesRev integer,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table TravelTimesForTrip_to_TravelTimesForPath_joinTable (
        TravelTimesForTrips_id integer not null,
        travelTimesForStopPaths_id integer not null,
        listIndex integer not null,
        primary key (TravelTimesForTrips_id, listIndex)
    ) DEFAULT CHARSET=utf8;

    create table TravelTimesForTrips (
        id integer not null auto_increment,
        configRev integer,
        travelTimesRev integer,
        tripCreatedForId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        tripPatternId varchar(120) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table TripPattern_to_Path_joinTable (
        TripPatterns_id varchar(120) CHARACTER SET utf8 COLLATE utf8_bin not null,
        TripPatterns_configRev integer not null,
        stopPaths_tripPatternId varchar(120) CHARACTER SET utf8 COLLATE utf8_bin not null,
        stopPaths_stopPathId varchar(120) CHARACTER SET utf8 COLLATE utf8_bin not null,
        stopPaths_configRev integer not null,
        listIndex integer not null,
        primary key (TripPatterns_id, TripPatterns_configRev, listIndex)
    ) DEFAULT CHARSET=utf8;

    create table TripPatterns (
        id varchar(120) CHARACTER SET utf8 COLLATE utf8_bin not null,
        configRev integer not null,
        directionId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        maxLat double precision,
        maxLon double precision,
        minLat double precision,
        minLon double precision,
        headsign varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeShortName varchar(80) CHARACTER SET utf8 COLLATE utf8_bin,
        shapeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (id, configRev)
    ) DEFAULT CHARSET=utf8;

    create table Trip_scheduledTimesList (
        Trip_tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        Trip_startTime integer not null,
        Trip_configRev integer not null,
        arrivalTime integer,
        departureTime integer,
        scheduledTimesList_ORDER integer not null,
        primary key (Trip_tripId, Trip_startTime, Trip_configRev, scheduledTimesList_ORDER)
    ) DEFAULT CHARSET=utf8;

    create table Trips (
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        startTime integer not null,
        configRev integer not null,
        blockId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        directionId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        endTime integer,
        exactTimesHeadway bit,
        headsign varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        noSchedule bit,
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeShortName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        serviceId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        shapeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        tripShortName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        travelTimes_id integer,
        tripPattern_id varchar(120) CHARACTER SET utf8 COLLATE utf8_bin,
        tripPattern_configRev integer,
        primary key (tripId, startTime, configRev)
    ) DEFAULT CHARSET=utf8;

    create table VehicleConfigs (
        id varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        capacity integer,
        crushCapacity integer,
        description varchar(255) CHARACTER SET utf8 COLLATE utf8_bin,
        nonPassengerVehicle bit,
        trackerId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        type integer,
        primary key (id)
    ) DEFAULT CHARSET=utf8;

    create table VehicleEvents (
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        time datetime(3) not null,
        eventType varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        avlTime datetime(3),
        becameUnpredictable bit,
        blockId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        description longtext,
        lat double precision,
        lon double precision,
        predictable bit,
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeShortName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        serviceId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        stopId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        supervisor varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (vehicleId, time, eventType)
    ) DEFAULT CHARSET=utf8;

    create table VehicleStates (
        vehicleId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        avlTime datetime(3) not null,
        blockId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        isDelayed bit,
        isForSchedBasedPreds bit,
        isLayover bit,
        isPredictable bit,
        isWaitStop bit,
        routeId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        routeShortName varchar(80) CHARACTER SET utf8 COLLATE utf8_bin,
        schedAdh varchar(50) CHARACTER SET utf8 COLLATE utf8_bin,
        schedAdhMsec integer,
        schedAdhWithinBounds bit,
        tripId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        tripShortName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (vehicleId, avlTime)
    ) DEFAULT CHARSET=utf8;

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
