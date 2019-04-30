create table users (
   id bigint primary key default id_gen(),
   name text unique not null,
   created timestamp with time zone not null default now()
);
--;;

create table auth_tokens (
     id bigint primary key default id_gen(),
     user_id bigint not null unique references "users" ("id") on delete cascade,
     token uuid unique not null,
     created timestamp with time zone not null default now()
);
--;;

create table items (
   id bigint primary key default id_gen(),
   size bigint,
   token uuid unique not null,
   name text not null,
   hash text,
   creator_id bigint not null references "users" ("id") on delete set null,
   created timestamp with time zone not null default now(),
   expires_at timestamp with time zone
);
--;;

create unique index item_tokens on items (token);
--;;
create index user_items on items (creator_id);