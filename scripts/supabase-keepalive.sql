-- Ejecuta esto UNA vez en Supabase (SQL Editor) para crear la tabla que usa el keepalive.
create table if not exists public.keepalive (
  id        int primary key,
  last_ping timestamptz not null default now()
);

insert into public.keepalive (id, last_ping)
values (1, now())
on conflict (id) do update set last_ping = excluded.last_ping;
