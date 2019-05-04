create sequence stash.id_seq;
--;;
create or replace function stash.id_gen(out result bigint) as $$
declare
    id_epoch bigint := 1553394377671;
    seq_id bigint;
    now_millis bigint;
begin
    select nextval('id_seq') % 524288 into seq_id;
    select floor(extract(epoch from clock_timestamp()) * 1000) into now_millis;
    result := (now_millis - id_epoch) << 19; -- 45 bits of milliseconds
    result := result | (seq_id); -- 19 bits for seq ids per millis
end;
$$ language plpgsql;