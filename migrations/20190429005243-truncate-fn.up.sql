
create or replace function stash.truncate_tables()
returns text as
$func$
declare
    database_name text;
    table_names text;
    stmt text;
begin
    select into database_name current_database();
    if database_name not like 'stash_test_%' then
        raise notice 'not truncating because database (%) does not appear to be a test database', database_name;
        return '';
    end if;

    select
        into table_names
        string_agg(format('%I.%I', schemaname, tablename), ', ')
    from pg_tables
    where tableowner = 'stash'
    and schemaname = 'stash'
    and tablename not like '%migrations%';

    stmt := 'truncate table ' || table_names || ' cascade';
    raise notice 'executing: %', stmt;
    execute stmt;
    return table_names;
end
$func$ language plpgsql;

