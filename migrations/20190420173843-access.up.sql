create type stash.access_kind as enum ('create', 'retrieve', 'delete');
--;;

create table stash.access (
    id bigint primary key default stash.id_gen(),
    item_id bigint not null,
    user_id bigint not null,
    kind stash.access_kind not null,
    created timestamp with time zone not null default now()
);