create table items (
    id bigserial primary key,
    size bigint not null,
    path text not null,
    stash_token uuid unique not null,
    supplied_token text not null,
    content_hash text,
    created timestamp with time zone not null default now(),
    expires_at timestamp with time zone
);

create unique index token_pair on items (stash_token, supplied_token);
