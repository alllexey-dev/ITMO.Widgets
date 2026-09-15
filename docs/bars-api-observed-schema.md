# БАРС: полная наблюдённая структура JSON

Приложение к [описанию API](bars-api.md). Источник: HAR от 15 сентября 2026 года.

Это инвентаризация **всех** JSON request bodies и успешных JSON response shapes выбранного HAR,
а не OpenAPI и не гарантия required-полей. Значения не экспортируются.
Одинаковые запросы и одинаковые ответы не дедуплицированы: счётчики показывают
число наблюдений, а не независимых сущностей. Индексы H — zero-based.

- `integer` и `number` различают записанные JSON-числа; для баллов нужен тип,
  сохраняющий дробную часть, для ID — достаточно широкий целочисленный тип.
- «Отсутствует» считается только относительно реально наблюдённого объекта-родителя.
  Отсутствие родителя не прибавляется к отсутствию каждого его дочернего поля.
- `null` не подтверждает ненулевой тип. Пустой массив не раскрывает тип элементов.
- Нулевой счётчик отсутствия не доказывает обязательность поля в остальных сценариях.
- HTTP status 0 не является ответом сервера и не участвует в схемах.

## Покрытие

| Метод / путь | Записей H | HTTP-статусы: количество |
|---|---:|---|
| `GET /runtime-config.json` | 5 | 200: 5 |
| `GET /backend/rest/login` | 1 | 200: 1 |
| `GET /backend/rest/config/` | 5 | 200: 5 |
| `GET /backend/rest/users/current_user/` | 20 | 0: 4, 200: 16 |
| `POST /backend/rest/config/personal` | 4 | 0: 2, 200: 2 |
| `POST /backend/rest/users/set_selected_role` | 2 | 200: 2 |
| `GET /backend/rest/journal/disciplines` | 23 | 0: 7, 200: 16 |
| `GET /backend/rest/journal/groups-and-flows` | 16 | 0: 2, 200: 14 |
| `GET /backend/rest/marks/{checkpointPlanId}/{type}/{identifier}/student` | 4 | 0: 2, 200: 2 |
| `GET /backend/rest/deadline/{type}/{identifier}/{checkpointPlanId}` | 2 | 200: 2 |
| `GET /backend/rest/checkpoint_plans/{checkpointPlanId}` | 1 | 200: 1 |
| `GET /backend/rest/checkpoint_types` | 5 | 200: 5 |
| `GET /backend/rest/deadline` | 1 | 200: 1 |

## `GET /runtime-config.json`

H: 4, 20, 34, 62, 121.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | object | 5 | 0 | — |
| `$.BACKEND_URL` | string | 5 | 0 | 0 |
| `$.USER_CHECK_INTERVAL` | string | 5 | 0 | 0 |
| `$.OIDC_ISSUER_URL` | string | 5 | 0 | 0 |
| `$.OIDC_CLIENT_ID` | string | 5 | 0 | 0 |
| `$.OIDC_REDIRECT_URI` | string | 5 | 0 | 0 |
| `$.OIDC_LOGOUT_URI` | string | 5 | 0 | 0 |
| `$.GA_MEASUREMENT_ID` | string | 5 | 0 | 0 |

## `GET /backend/rest/login`

H: 8.

Query names: `code`, `customRedirectUri`.

У успешного ответа пустое тело. Для login результат авторизации находится
в response header `authorization`; у выбора роли JSON-результата нет.

## `GET /backend/rest/config/`

H: 10, 24, 38, 69, 129.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | array | 5 | 0 | — |
| `$[]` | object | 65 | 0 | — |
| `$[].id` | integer | 65 | 0 | 0 |
| `$[].name` | string | 65 | 0 | 0 |
| `$[].value` | null / string | 65 | 5 | 0 |

## `GET /backend/rest/users/current_user/`

H: 11, 12, 14, 16, 25, 26, 28, 30, 39, 40, 42, 70, 71, 74, 75, 100, 130, 131, 134, 135.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | object | 16 | 0 | — |
| `$.id` | integer | 16 | 0 | 0 |
| `$.login` | string | 16 | 0 | 0 |
| `$.created_by` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.updated_by` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.created_at` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.updated_at` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.user_roles` | array | 16 | 0 | 0 |
| `$.user_roles[]` | object | 16 | 0 | — |
| `$.user_roles[].id` | integer | 16 | 0 | 0 |
| `$.user_roles[].name` | string | 16 | 0 | 0 |
| `$.user_roles[].created_by` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.user_roles[].updated_by` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.user_roles[].created_at` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.user_roles[].updated_at` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.user_roles[].main_user` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.selected_role` | object | 16 | 0 | 0 |
| `$.selected_role.id` | integer | 16 | 0 | 0 |
| `$.selected_role.name` | string | 16 | 0 | 0 |
| `$.selected_role.locked` | boolean | 16 | 0 | 0 |
| `$.selected_role.selected` | boolean | 16 | 0 | 0 |
| `$.selected_role.created_by` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.selected_role.updated_by` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.selected_role.created_at` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.selected_role.updated_at` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.selected_role.main_user` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.selected_role.requires_main_user_user_role` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.selected_role.allows_white_list` | boolean | 16 | 0 | 0 |
| `$.selected_role.allows_multiple` | boolean | 16 | 0 | 0 |
| `$.selected_role.white_list` | array (только пустой) | 16 | 0 | 0 |
| `$.first_name` | string | 16 | 0 | 0 |
| `$.middle_name` | string | 16 | 0 | 0 |
| `$.last_name` | string | 16 | 0 | 0 |
| `$.selected_year` | string | 16 | 0 | 0 |
| `$.selected_term` | integer | 16 | 0 | 0 |
| `$.personal_config` | array | 16 | 0 | 0 |
| `$.can_change_user` | boolean | 16 | 0 | 0 |
| `$.super_user_personal_number` | null (ненулевой тип неизвестен) | 16 | 16 | 0 |
| `$.restricted_to_have_read_only_access` | boolean | 16 | 0 | 0 |
| `$.personal_config[]` | object | 28 | 0 | — |
| `$.personal_config[].id` | integer | 28 | 0 | 0 |
| `$.personal_config[].name` | string | 28 | 0 | 0 |
| `$.personal_config[].value` | string | 28 | 0 | 0 |

## `POST /backend/rest/config/personal`

H: 13, 15, 27, 29.

### Request body

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | object | 4 | 0 | — |
| `$.name` | string | 4 | 0 | 0 |
| `$.value` | string | 4 | 0 | 0 |

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | object | 2 | 0 | — |
| `$.id` | integer | 2 | 0 | 0 |
| `$.name` | string | 2 | 0 | 0 |
| `$.value` | string | 2 | 0 | 0 |

## `POST /backend/rest/users/set_selected_role`

H: 41, 99.

### Request body

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | object | 2 | 0 | — |
| `$.id` | integer | 2 | 0 | 0 |
| `$.name` | string | 2 | 0 | 0 |
| `$.created_by` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.updated_by` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.created_at` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.updated_at` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.main_user` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |

У успешного ответа пустое тело. Для login результат авторизации находится
в response header `authorization`; у выбора роли JSON-результата нет.

## `GET /backend/rest/journal/disciplines`

H: 48, 51, 53, 54, 56, 72, 77, 78, 80, 81, 82, 94, 101, 104, 106, 108, 109, 132, 137, 138, 140, 141, 143.

Query names: `identifier`, `name`, `type`, `withCheckpointPlansOnly`.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | array | 16 | 0 | — |
| `$[]` | object | 86 | 0 | — |
| `$[].id` | integer | 86 | 0 | 0 |
| `$[].name` | string | 86 | 0 | 0 |
| `$[].terms` | array | 86 | 0 | 0 |
| `$[].terms[]` | integer | 132 | 0 | — |
| `$[].checkpoint_plan_ids` | array | 86 | 0 | 0 |
| `$[].checkpoint_plan_ids[]` | integer | 86 | 0 | — |

## `GET /backend/rest/journal/groups-and-flows`

H: 49, 50, 52, 55, 57, 73, 76, 95, 102, 103, 105, 107, 110, 113, 133, 136.

Query names: `disciplineId`, `name`.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | array | 14 | 0 | — |
| `$[]` | object | 134 | 0 | — |
| `$[].type` | string | 134 | 0 | 0 |
| `$[].name` | string | 134 | 0 | 0 |
| `$[].identifier` | string | 134 | 0 | 0 |
| `$[].checkpoint_plan_ids` | array | 134 | 0 | 0 |
| `$[].checkpoint_plan_ids[]` | integer | 134 | 0 | — |

## `GET /backend/rest/marks/{checkpointPlanId}/{type}/{identifier}/student`

H: 58, 79, 111, 139.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | object | 2 | 0 | — |
| `$.students` | array | 2 | 0 | 0 |
| `$.students[]` | object | 2 | 0 | — |
| `$.students[].student_name` | string | 2 | 0 | 0 |
| `$.students[].student_login` | string | 2 | 0 | 0 |
| `$.students[].student_id` | integer | 2 | 0 | 0 |
| `$.students[].accessibility` | object | 2 | 0 | 0 |
| `$.students[].accessibility.can_edit_current_marks` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_edit_additional_marks` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_edit_course_marks` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_edit_final_marks` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_edit_final1_marks` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_edit_final2_marks` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_approve_marks` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_approve_retry_marks` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_approve_course_project` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.can_approve_retry_course_project` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.has_unfilled_key_checkpoints` | boolean | 2 | 0 | 0 |
| `$.students[].accessibility.course_project_theme_not_approved` | boolean | 2 | 0 | 0 |
| `$.students[].marks` | object | 2 | 0 | 0 |
| `$.students[].marks.regular` | array | 2 | 0 | 0 |
| `$.students[].marks.regular[]` | object | 20 | 0 | — |
| `$.students[].marks.regular[].id` | integer | 20 | 0 | 0 |
| `$.students[].marks.regular[].checkpoint_id` | integer | 20 | 0 | 0 |
| `$.students[].marks.regular[].checkpoint_plan_id` | integer | 20 | 0 | 0 |
| `$.students[].marks.regular[].mark` | number | 20 | 0 | 0 |
| `$.students[].marks.regular[].created_by` | integer | 20 | 0 | 0 |
| `$.students[].marks.regular[].updated_by` | integer | 20 | 0 | 0 |
| `$.students[].marks.regular[].created_at` | integer | 20 | 0 | 0 |
| `$.students[].marks.regular[].updated_at` | integer | 20 | 0 | 0 |
| `$.students[].marks.regular[].type` | string | 20 | 0 | 0 |
| `$.students[].marks.regular[].created_by_name` | string | 20 | 0 | 0 |
| `$.students[].marks.regular[].updated_by_name` | string | 20 | 0 | 0 |
| `$.students[].marks.regular[].is_absent` | boolean | 20 | 0 | 0 |
| `$.students[].marks.regular[].is_not_bigger_than_max` | boolean | 20 | 0 | 0 |
| `$.students[].marks.active_approvals` | array | 2 | 0 | 0 |
| `$.students[].marks.active_approvals[]` | object | 3 | 0 | — |
| `$.students[].marks.active_approvals[].id` | integer | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].student_id` | integer | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].student_login` | string | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].checkpoint_plan_id` | integer | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].attempt` | integer | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].marks_sum` | number | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].mark_string` | string | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].is_active` | boolean | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].is_absent` | boolean | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].was_recalculated` | boolean | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].created_by_name` | string | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].created_by` | integer | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].updated_by` | integer | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].created_at` | integer | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].updated_at` | integer | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].updated_by_name` | string | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].course` | boolean | 3 | 0 | 0 |
| `$.students[].marks.active_approvals[].is_invalid` | boolean | 3 | 0 | 0 |
| `$.students[].marks.regularSum` | number | 2 | 0 | 0 |
| `$.students[].marks.total` | number | 2 | 0 | 0 |
| `$.students[].wants_to_increase_marks` | boolean | 2 | 0 | 0 |
| `$.headers` | object | 2 | 0 | 0 |
| `$.headers.plan` | object | 2 | 0 | 0 |
| `$.headers.plan.id` | integer | 2 | 0 | 0 |
| `$.headers.plan.programs` | array (только пустой) | 2 | 0 | 0 |
| `$.headers.plan.components` | array (только пустой) | 2 | 0 | 0 |
| `$.headers.plan.terms` | array | 2 | 0 | 0 |
| `$.headers.plan.terms[]` | integer | 4 | 0 | — |
| `$.headers.plan.year` | string | 2 | 0 | 0 |
| `$.headers.plan.created_by` | integer | 2 | 0 | 0 |
| `$.headers.plan.updated_by` | integer | 2 | 0 | 0 |
| `$.headers.plan.created_at` | integer | 2 | 0 | 0 |
| `$.headers.plan.updated_at` | integer | 2 | 0 | 0 |
| `$.headers.plan.gid` | string | 2 | 0 | 0 |
| `$.headers.plan.discipline` | object | 2 | 0 | 0 |
| `$.headers.plan.discipline.id` | integer | 2 | 0 | 0 |
| `$.headers.plan.discipline.name` | string | 2 | 0 | 0 |
| `$.headers.plan.discipline.term` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.discipline.course_project` | boolean | 2 | 0 | 0 |
| `$.headers.plan.point_distribution` | integer | 2 | 0 | 0 |
| `$.headers.plan.alternate_methods` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.additional_points` | boolean | 2 | 0 | 0 |
| `$.headers.plan.status` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.regular_checkpoints` | array | 2 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[]` | object | 20 | 0 | — |
| `$.headers.plan.regular_checkpoints[].id` | integer | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].gid` | string | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].name` | string | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].type` | string | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].week` | integer | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].group` | boolean | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].key` | boolean | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].created_by` | integer | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].updated_by` | integer | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].created_at` | integer | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].updated_at` | integer | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].type_id` | integer | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].min_grade` | number | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].max_grade` | number | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].test_id` | null (ненулевой тип неизвестен) | 20 | 20 | 0 |
| `$.headers.plan.regular_checkpoints[].test_name` | null (ненулевой тип неизвестен) | 20 | 20 | 0 |
| `$.headers.plan.regular_checkpoints[].sub_checkpoints` | array (только пустой) | 20 | 0 | 0 |
| `$.headers.plan.regular_checkpoints[].parent_checkpoint_id` | null (ненулевой тип неизвестен) | 20 | 20 | 0 |
| `$.headers.plan.regular_checkpoints[].max_sub_checkpoints_fillable` | null (ненулевой тип неизвестен) | 20 | 20 | 0 |
| `$.headers.plan.final_checkpoint` | object | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.id` | integer | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.gid` | string | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.name` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.final_checkpoint.type` | string | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.week` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.final_checkpoint.group` | boolean | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.key` | boolean | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.created_by` | integer | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.updated_by` | integer | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.created_at` | integer | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.updated_at` | integer | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.type_id` | integer | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.min_grade` | number | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.max_grade` | number | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.test_id` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.final_checkpoint.test_name` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.final_checkpoint.sub_checkpoints` | array (только пустой) | 2 | 0 | 0 |
| `$.headers.plan.final_checkpoint.parent_checkpoint_id` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.final_checkpoint.max_sub_checkpoints_fillable` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.course_project_checkpoint` | null (ненулевой тип неизвестен) | 2 | 2 | 0 |
| `$.headers.plan.has_course_project` | boolean | 2 | 0 | 0 |
| `$.headers.deadlines` | array (только пустой) | 2 | 0 | 0 |
| `$.headers.type` | string | 2 | 0 | 0 |
| `$.headers.identifier` | string | 2 | 0 | 0 |
| `$.headers.name` | string | 2 | 0 | 0 |
| `$.students[].marks.final` | object | 1 | 0 | 1 |
| `$.students[].marks.final.id` | integer | 1 | 0 | 0 |
| `$.students[].marks.final.checkpoint_id` | integer | 1 | 0 | 0 |
| `$.students[].marks.final.checkpoint_plan_id` | integer | 1 | 0 | 0 |
| `$.students[].marks.final.mark` | number | 1 | 0 | 0 |
| `$.students[].marks.final.created_by` | integer | 1 | 0 | 0 |
| `$.students[].marks.final.updated_by` | integer | 1 | 0 | 0 |
| `$.students[].marks.final.created_at` | integer | 1 | 0 | 0 |
| `$.students[].marks.final.updated_at` | integer | 1 | 0 | 0 |
| `$.students[].marks.final.type` | string | 1 | 0 | 0 |
| `$.students[].marks.final.created_by_name` | string | 1 | 0 | 0 |
| `$.students[].marks.final.updated_by_name` | string | 1 | 0 | 0 |
| `$.students[].marks.final.is_absent` | boolean | 1 | 0 | 0 |
| `$.students[].marks.final.is_not_bigger_than_max` | boolean | 1 | 0 | 0 |

## `GET /backend/rest/deadline/{type}/{identifier}/{checkpointPlanId}`

H: 83, 142.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | array (только пустой) | 2 | 0 | — |

## `GET /backend/rest/checkpoint_plans/{checkpointPlanId}`

H: 86.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | object | 1 | 0 | — |
| `$.id` | integer | 1 | 0 | 0 |
| `$.programs` | array (только пустой) | 1 | 0 | 0 |
| `$.components` | array (только пустой) | 1 | 0 | 0 |
| `$.terms` | array | 1 | 0 | 0 |
| `$.terms[]` | integer | 3 | 0 | — |
| `$.year` | string | 1 | 0 | 0 |
| `$.created_by` | integer | 1 | 0 | 0 |
| `$.updated_by` | integer | 1 | 0 | 0 |
| `$.created_at` | integer | 1 | 0 | 0 |
| `$.updated_at` | integer | 1 | 0 | 0 |
| `$.gid` | string | 1 | 0 | 0 |
| `$.discipline` | object | 1 | 0 | 0 |
| `$.discipline.id` | integer | 1 | 0 | 0 |
| `$.discipline.name` | string | 1 | 0 | 0 |
| `$.discipline.term` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.discipline.course_project` | boolean | 1 | 0 | 0 |
| `$.point_distribution` | integer | 1 | 0 | 0 |
| `$.alternate_methods` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.additional_points` | boolean | 1 | 0 | 0 |
| `$.status` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.regular_checkpoints` | array | 1 | 0 | 0 |
| `$.regular_checkpoints[]` | object | 10 | 0 | — |
| `$.regular_checkpoints[].id` | integer | 10 | 0 | 0 |
| `$.regular_checkpoints[].gid` | string | 10 | 0 | 0 |
| `$.regular_checkpoints[].name` | string | 10 | 0 | 0 |
| `$.regular_checkpoints[].type` | string | 10 | 0 | 0 |
| `$.regular_checkpoints[].week` | integer | 10 | 0 | 0 |
| `$.regular_checkpoints[].group` | boolean | 10 | 0 | 0 |
| `$.regular_checkpoints[].key` | boolean | 10 | 0 | 0 |
| `$.regular_checkpoints[].created_by` | integer | 10 | 0 | 0 |
| `$.regular_checkpoints[].updated_by` | integer | 10 | 0 | 0 |
| `$.regular_checkpoints[].created_at` | integer | 10 | 0 | 0 |
| `$.regular_checkpoints[].updated_at` | integer | 10 | 0 | 0 |
| `$.regular_checkpoints[].type_id` | integer | 10 | 0 | 0 |
| `$.regular_checkpoints[].min_grade` | number | 10 | 0 | 0 |
| `$.regular_checkpoints[].max_grade` | number | 10 | 0 | 0 |
| `$.regular_checkpoints[].test_id` | null (ненулевой тип неизвестен) | 10 | 10 | 0 |
| `$.regular_checkpoints[].test_name` | null (ненулевой тип неизвестен) | 10 | 10 | 0 |
| `$.regular_checkpoints[].sub_checkpoints` | array (только пустой) | 10 | 0 | 0 |
| `$.regular_checkpoints[].parent_checkpoint_id` | null (ненулевой тип неизвестен) | 10 | 10 | 0 |
| `$.regular_checkpoints[].max_sub_checkpoints_fillable` | null (ненулевой тип неизвестен) | 10 | 10 | 0 |
| `$.final_checkpoint` | object | 1 | 0 | 0 |
| `$.final_checkpoint.id` | integer | 1 | 0 | 0 |
| `$.final_checkpoint.gid` | string | 1 | 0 | 0 |
| `$.final_checkpoint.name` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.final_checkpoint.type` | string | 1 | 0 | 0 |
| `$.final_checkpoint.week` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.final_checkpoint.group` | boolean | 1 | 0 | 0 |
| `$.final_checkpoint.key` | boolean | 1 | 0 | 0 |
| `$.final_checkpoint.created_by` | integer | 1 | 0 | 0 |
| `$.final_checkpoint.updated_by` | integer | 1 | 0 | 0 |
| `$.final_checkpoint.created_at` | integer | 1 | 0 | 0 |
| `$.final_checkpoint.updated_at` | integer | 1 | 0 | 0 |
| `$.final_checkpoint.type_id` | integer | 1 | 0 | 0 |
| `$.final_checkpoint.min_grade` | number | 1 | 0 | 0 |
| `$.final_checkpoint.max_grade` | number | 1 | 0 | 0 |
| `$.final_checkpoint.test_id` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.final_checkpoint.test_name` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.final_checkpoint.sub_checkpoints` | array (только пустой) | 1 | 0 | 0 |
| `$.final_checkpoint.parent_checkpoint_id` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.final_checkpoint.max_sub_checkpoints_fillable` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.course_project_checkpoint` | null (ненулевой тип неизвестен) | 1 | 1 | 0 |
| `$.has_course_project` | boolean | 1 | 0 | 0 |

## `GET /backend/rest/checkpoint_types`

H: 87, 88, 89, 90, 91.

Query names: `type`.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | array | 5 | 0 | — |
| `$[]` | object | 102 | 0 | — |
| `$[].id` | integer | 102 | 0 | 0 |
| `$[].name` | string | 102 | 0 | 0 |
| `$[].type` | string | 102 | 0 | 0 |
| `$[].ordering` | integer | 102 | 0 | 0 |
| `$[].archived` | boolean | 102 | 0 | 0 |
| `$[].created_by` | integer / null | 102 | 81 | 0 |
| `$[].updated_by` | integer / null | 102 | 81 | 0 |
| `$[].created_at` | integer / null | 102 | 81 | 0 |
| `$[].updated_at` | integer / null | 102 | 81 | 0 |

## `GET /backend/rest/deadline`

H: 96.

### Response body (HTTP 200)

| JSON path | Wire-типы | Присутствует | null | Отсутствует в объекте-родителе |
|---|---|---:|---:|---:|
| `$` | array (только пустой) | 1 | 0 | — |
