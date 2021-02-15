
    create table ApiKeys (
        applicationName varchar(80) CHARACTER SET utf8 COLLATE utf8_bin not null,
        applicationKey varchar(20) CHARACTER SET utf8 COLLATE utf8_bin,
        applicationUrl varchar(80) CHARACTER SET utf8 COLLATE utf8_bin,
        description longtext,
        email varchar(80) CHARACTER SET utf8 COLLATE utf8_bin,
        phone varchar(80) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (applicationName)
    ) DEFAULT CHARSET=utf8;

    create table WebAgencies (
        agencyId varchar(60) CHARACTER SET utf8 COLLATE utf8_bin not null,
        active bit,
        dbEncryptedPassword varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        dbHost varchar(120) CHARACTER SET utf8 COLLATE utf8_bin,
        dbName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        dbType varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        dbUserName varchar(60) CHARACTER SET utf8 COLLATE utf8_bin,
        hostName varchar(120) CHARACTER SET utf8 COLLATE utf8_bin,
        primary key (agencyId)
    ) DEFAULT CHARSET=utf8;
