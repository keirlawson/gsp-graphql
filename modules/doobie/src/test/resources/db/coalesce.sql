CREATE TABLE r (
    id TEXT PRIMARY KEY
);

CREATE TABLE ca (
    id TEXT PRIMARY KEY,
    rid TEXT NOT NULL,
    a INTEGER NOT NULL
);

CREATE TABLE cb (
    id TEXT PRIMARY KEY,
    rid TEXT NOT NULL,
    b BOOLEAN NOT NULL
);

COPY r (id) FROM STDIN WITH DELIMITER '|';
R1
R2
R3
\.

COPY ca (id, rid, a) FROM STDIN WITH DELIMITER '|';
CA1a|R1|10
CA1b|R1|11
CA2|R2|20
CA3|R3|30
\.

COPY cb (id, rid, b) FROM STDIN WITH DELIMITER '|';
CB2a|R2|TRUE
CB2b|R2|FALSE
CB3|R3|TRUE
\.
