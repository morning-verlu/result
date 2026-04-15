create or replace function public.app_releases_disable_old_enabled()
returns trigger
language plpgsql
as $$
begin
  if new.enabled then
    update public.app_releases
    set enabled = false
    where package_name = new.package_name
      and id <> new.id
      and enabled = true;
  end if;
  return new;
end;
$$;

drop trigger if exists app_releases_disable_old_enabled_trg on public.app_releases;
create trigger app_releases_disable_old_enabled_trg
before insert or update of enabled, package_name
on public.app_releases
for each row
execute function public.app_releases_disable_old_enabled();

with ranked as (
  select
    id,
    row_number() over (
      partition by package_name
      order by version_code desc, published_at desc, created_at desc
    ) as rn
  from public.app_releases
  where enabled = true
)
update public.app_releases t
set enabled = false
from ranked r
where t.id = r.id
  and r.rn > 1;
