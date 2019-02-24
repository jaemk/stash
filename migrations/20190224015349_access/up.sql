create type access_kind as enum ('create', 'retrieve', 'delete');

create table access (
    id bigserial primary key,
    item bigint not null,
    app_user bigint not null,
    kind access_kind not null,
    created timestamp with time zone not null default now()
);
