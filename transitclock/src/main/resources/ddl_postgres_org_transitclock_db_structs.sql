
    create table ActiveRevisions (
        id int4 not null,
        configRev int4,
        travelTimesRev int4,
        primary key (id)
    );

    create table Agencies (
        configRev int4 not null,
        agencyName varchar(60) not null,
        agencyId varchar(60) not null,
        agencyFareUrl varchar(255),
        agencyLang varchar(15),
        agencyPhone varchar(15),
        agencyTimezone varchar(40),
        agencyUrl varchar(255),
        maxLat float8,
        maxLon float8,
        minLat float8,
        minLon float8,
        primary key (configRev, agencyName, agencyId)
    );

    create table ArrivalsDepartures (
        DTYPE varchar(31) not null,
        vehicleId varchar(60) not null,
        tripId varchar(60) not null,
        time timestamp not null,
        stopId varchar(60) not null,
        isArrival boolean not null,
        gtfsStopSeq int4 not null,
        avlTime timestamp,
        blockId varchar(60),
        configRev int4,
        directionId varchar(60),
        freqStartTime timestamp,
        routeId varchar(60),
        routeShortName varchar(60),
        scheduledTime timestamp,
        serviceId varchar(60),
        stopOrder int4,
        stopPathIndex int4,
        stopPathLength float4,
        tripIndex int4,
        primary key (vehicleId, tripId, time, stopId, isArrival, gtfsStopSeq)
    );

    create table AvlReports (
        vehicleId varchar(60) not null,
        time timestamp not null,
        assignmentId varchar(60),
        assignmentType varchar(40),
        driverId varchar(60),
        field1Name varchar(60),
        field1Value varchar(60),
        heading float4,
        licensePlate varchar(10),
        lat float8,
        lon float8,
        passengerCount int4,
        passengerFullness float4,
        source varchar(10),
        speed float4,
        timeProcessed timestamp,
        primary key (vehicleId, time)
    );

    create table Block_to_Trip_joinTable (
        Blocks_serviceId varchar(60) not null,
        Blocks_configRev int4 not null,
        Blocks_blockId varchar(60) not null,
        trips_tripId varchar(60) not null,
        trips_startTime int4 not null,
        trips_configRev int4 not null,
        listIndex int4 not null,
        primary key (Blocks_serviceId, Blocks_configRev, Blocks_blockId, listIndex)
    );

    create table Blocks (
        serviceId varchar(60) not null,
        configRev int4 not null,
        blockId varchar(60) not null,
        endTime int4,
        routeIds bytea,
        startTime int4,
        primary key (serviceId, configRev, blockId)
    );

    create table CalendarDates (
        serviceId varchar(60) not null,
        date date not null,
        configRev int4 not null,
        exceptionType varchar(2),
        primary key (serviceId, date, configRev)
    );

    create table Calendars (
        wednesday boolean not null,
        tuesday boolean not null,
        thursday boolean not null,
        sunday boolean not null,
        startDate date not null,
        serviceId varchar(60) not null,
        saturday boolean not null,
        monday boolean not null,
        friday boolean not null,
        endDate date not null,
        configRev int4 not null,
        primary key (wednesday, tuesday, thursday, sunday, startDate, serviceId, saturday, monday, friday, endDate, configRev)
    );

    create table ConfigRevision (
        configRev int4 not null,
        notes varchar(512),
        processedTime timestamp,
        zipFileLastModifiedTime timestamp,
        primary key (configRev)
    );

    create table DbTest (
        id int4 not null,
        primary key (id)
    );

    create table FareAttributes (
        fareId varchar(60) not null,
        configRev int4 not null,
        currencyType varchar(3),
        paymentMethod varchar(255),
        price float4,
        transferDuration int4,
        transfers varchar(255),
        primary key (fareId, configRev)
    );

    create table FareRules (
        routeId varchar(60) not null,
        originId varchar(60) not null,
        fareId varchar(60) not null,
        destinationId varchar(60) not null,
        containsId varchar(60) not null,
        configRev int4 not null,
        primary key (routeId, originId, fareId, destinationId, containsId, configRev)
    );

    create table Frequencies (
        tripId varchar(60) not null,
        startTime int4 not null,
        configRev int4 not null,
        endTime int4,
        exactTimes boolean,
        headwaySecs int4,
        primary key (tripId, startTime, configRev)
    );

    create table Headway (
        id int8 not null,
        average float8,
        coefficientOfVariation float8,
        configRev int4,
        creationTime timestamp,
        firstDeparture timestamp,
        headway float8,
        numVehicles int4,
        otherVehicleId varchar(60),
        routeId varchar(60),
        secondDeparture timestamp,
        stopId varchar(60),
        tripId varchar(60),
        variance float8,
        vehicleId varchar(60),
        primary key (id)
    );

    create table HoldingTimes (
        id int8 not null,
        arrivalPredictionUsed boolean,
        arrivalTime timestamp,
        arrivalUsed boolean,
        configRev int4,
        creationTime timestamp,
        hasD1 boolean,
        holdingTime timestamp,
        numberPredictionsUsed int4,
        routeId varchar(60),
        stopId varchar(60),
        tripId varchar(60),
        vehicleId varchar(60),
        primary key (id)
    );

    create table Matches (
        vehicleId varchar(60) not null,
        avlTime timestamp not null,
        atStop boolean,
        blockId varchar(60),
        configRev int4,
        distanceAlongSegment float4,
        distanceAlongStopPath float4,
        segmentIndex int4,
        serviceId varchar(255),
        stopPathIndex int4,
        tripId varchar(60),
        primary key (vehicleId, avlTime)
    );

    create table MeasuredArrivalTimes (
        time timestamp not null,
        stopId varchar(60) not null,
        directionId varchar(60),
        headsign varchar(60),
        routeId varchar(60),
        routeShortName varchar(60),
        primary key (time, stopId)
    );

    create table MonitoringEvents (
        type varchar(40) not null,
        time timestamp not null,
        message varchar(512),
        triggered boolean,
        value float8,
        primary key (type, time)
    );

    create table PredictionAccuracy (
        id int8 not null,
        affectedByWaitStop boolean,
        arrivalDepartureTime timestamp,
        directionId varchar(60),
        predictedTime timestamp,
        predictionAccuracyMsecs int4,
        predictionReadTime timestamp,
        predictionSource varchar(60),
        routeId varchar(60),
        routeShortName varchar(60),
        stopId varchar(60),
        tripId varchar(60),
        vehicleId varchar(60),
        primary key (id)
    );

    create table Predictions (
        id int8 not null,
        affectedByWaitStop boolean,
        avlTime timestamp,
        configRev int4,
        creationTime timestamp,
        gtfsStopSeq int4,
        isArrival boolean,
        predictionTime timestamp,
        routeId varchar(60),
        schedBasedPred boolean,
        stopId varchar(60),
        tripId varchar(60),
        vehicleId varchar(60),
        primary key (id)
    );

    create table Routes (
        id varchar(60) not null,
        configRev int4 not null,
        color varchar(10),
        description varchar(1024),
        maxLat float8,
        maxLon float8,
        minLat float8,
        minLon float8,
        hidden boolean,
        longName varchar(255),
        maxDistance float8,
        name varchar(255),
        routeOrder int4,
        shortName varchar(255),
        textColor varchar(10),
        type varchar(2),
        primary key (id, configRev)
    );

    create table StopPathPredictions (
        id int8 not null,
        algorithm varchar(255),
        creationTime timestamp,
        predictionTime float8,
        startTime int4,
        stopPathIndex int4,
        travelTime boolean,
        tripId varchar(60),
        vehicleId varchar(255),
        primary key (id)
    );

    create table StopPath_locations (
        StopPath_tripPatternId varchar(120) not null,
        StopPath_stopPathId varchar(120) not null,
        StopPath_configRev int4 not null,
        lat float8,
        lon float8,
        locations_ORDER int4 not null,
        primary key (StopPath_tripPatternId, StopPath_stopPathId, StopPath_configRev, locations_ORDER)
    );

    create table StopPaths (
        tripPatternId varchar(120) not null,
        stopPathId varchar(120) not null,
        configRev int4 not null,
        breakTime int4,
        gtfsStopSeq int4,
        lastStopInTrip boolean,
        layoverStop boolean,
        maxDistance float8,
        maxSpeed float8,
        pathLength float8,
        routeId varchar(60),
        scheduleAdherenceStop boolean,
        stopId varchar(60),
        waitStop boolean,
        primary key (tripPatternId, stopPathId, configRev)
    );

    create table Stops (
        id varchar(60) not null,
        configRev int4 not null,
        code int4,
        hidden boolean,
        layoverStop boolean,
        lat float8,
        lon float8,
        name varchar(255),
        timepointStop boolean,
        waitStop boolean,
        primary key (id, configRev)
    );

    create table Transfers (
        toStopId varchar(60) not null,
        fromStopId varchar(60) not null,
        configRev int4 not null,
        minTransferTime int4,
        transferType varchar(1),
        primary key (toStopId, fromStopId, configRev)
    );

    create table TravelTimesForStopPaths (
        id int4 not null,
        configRev int4,
        daysOfWeekOverride int2,
        howSet varchar(5),
        stopPathId varchar(120),
        stopTimeMsec int4,
        travelTimeSegmentLength float4,
        travelTimesMsec bytea,
        travelTimesRev int4,
        primary key (id)
    );

    create table TravelTimesForTrip_to_TravelTimesForPath_joinTable (
        TravelTimesForTrips_id int4 not null,
        travelTimesForStopPaths_id int4 not null,
        listIndex int4 not null,
        primary key (TravelTimesForTrips_id, listIndex)
    );

    create table TravelTimesForTrips (
        id int4 not null,
        configRev int4,
        travelTimesRev int4,
        tripCreatedForId varchar(60),
        tripPatternId varchar(120),
        primary key (id)
    );

    create table TripPattern_to_Path_joinTable (
        TripPatterns_id varchar(120) not null,
        TripPatterns_configRev int4 not null,
        stopPaths_tripPatternId varchar(120) not null,
        stopPaths_stopPathId varchar(120) not null,
        stopPaths_configRev int4 not null,
        listIndex int4 not null,
        primary key (TripPatterns_id, TripPatterns_configRev, listIndex)
    );

    create table TripPatterns (
        id varchar(120) not null,
        configRev int4 not null,
        directionId varchar(60),
        maxLat float8,
        maxLon float8,
        minLat float8,
        minLon float8,
        headsign varchar(255),
        routeId varchar(60),
        routeShortName varchar(80),
        shapeId varchar(60),
        primary key (id, configRev)
    );

    create table Trip_scheduledTimesList (
        Trip_tripId varchar(60) not null,
        Trip_startTime int4 not null,
        Trip_configRev int4 not null,
        arrivalTime int4,
        departureTime int4,
        scheduledTimesList_ORDER int4 not null,
        primary key (Trip_tripId, Trip_startTime, Trip_configRev, scheduledTimesList_ORDER)
    );

    create table Trips (
        tripId varchar(60) not null,
        startTime int4 not null,
        configRev int4 not null,
        blockId varchar(60),
        directionId varchar(60),
        endTime int4,
        exactTimesHeadway boolean,
        headsign varchar(255),
        noSchedule boolean,
        routeId varchar(60),
        routeShortName varchar(60),
        serviceId varchar(60),
        shapeId varchar(60),
        tripShortName varchar(60),
        travelTimes_id int4,
        tripPattern_id varchar(120),
        tripPattern_configRev int4,
        primary key (tripId, startTime, configRev)
    );

    create table VehicleConfigs (
        id varchar(60) not null,
        capacity int4,
        crushCapacity int4,
        description varchar(255),
        nonPassengerVehicle boolean,
        trackerId varchar(60),
        type int4,
        primary key (id)
    );

    create table VehicleEvents (
        vehicleId varchar(60) not null,
        time timestamp not null,
        eventType varchar(60) not null,
        avlTime timestamp,
        becameUnpredictable boolean,
        blockId varchar(60),
        description varchar(500),
        lat float8,
        lon float8,
        predictable boolean,
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
        avlTime timestamp not null,
        blockId varchar(60),
        isDelayed boolean,
        isForSchedBasedPreds boolean,
        isLayover boolean,
        isPredictable boolean,
        isWaitStop boolean,
        routeId varchar(60),
        routeShortName varchar(80),
        schedAdh varchar(50),
        schedAdhMsec int4,
        schedAdhWithinBounds boolean,
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
