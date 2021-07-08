
    create table ApiKeys (
        applicationName varchar2(80 char) not null,
        applicationKey varchar2(20 char),
        applicationUrl varchar2(80 char),
        description varchar2(1000 char),
        email varchar2(80 char),
        phone varchar2(80 char),
        primary key (applicationName)
    );

    create table WebAgencies (
        agencyId varchar2(60 char) not null,
        active number(1,0),
        dbEncryptedPassword varchar2(60 char),
        dbHost varchar2(120 char),
        dbName varchar2(60 char),
        dbType varchar2(60 char),
        dbUserName varchar2(60 char),
        hostName varchar2(120 char),
        primary key (agencyId)
    );
