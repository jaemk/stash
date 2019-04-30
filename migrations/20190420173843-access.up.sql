create type access_kind as enum ('create', 'retrieve', 'delete');
--;;

create table access (
    id bigint primary key default id_gen(),
    item_id bigint not null,
    user_id bigint not null,
    kind access_kind not null,
    created timestamp with time zone not null default now()
);