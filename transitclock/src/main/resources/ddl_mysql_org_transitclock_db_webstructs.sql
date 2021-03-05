
    create table ApiKeys (
        applicationName varchar(80) not null,
        applicationKey varchar(20),
        applicationUrl varchar(80),
        description longtext,
        email varchar(80),
        phone varchar(80),
        primary key (applicationName)
    );

    create table WebAgencies (
        agencyId varchar(60) not null,
        active bit,
        dbEncryptedPassword varchar(60),
        dbHost varchar(120),
        dbName varchar(60),
        dbType varchar(60),
        dbUserName varchar(60),
        hostName varchar(120),
        primary key (agencyId)
    );
