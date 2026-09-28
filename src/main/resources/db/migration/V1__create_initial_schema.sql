-- 초기 스키마. 엔티티(ddl-auto: validate)와 컬럼 이름·타입·길이·NOT NULL을 맞춘다.
-- 적용된 뒤에는 이 파일을 고치지 말고 V2__...sql을 추가한다.

create table member (
    member_id           bigint       not null auto_increment primary key,
    kakao_id            varchar(64)  not null,
    nickname            varchar(50),
    residence           varchar(50),
    interest_categories varchar(200),
    created_at          datetime(6)  not null,
    updated_at          datetime(6)  not null,
    constraint uk_member_kakao unique (kakao_id)
) engine = InnoDB;

create table auth_session (
    session_id varchar(36) not null primary key,
    member_id  bigint      not null,
    expires_at datetime(6) not null,
    created_at datetime(6) not null,
    constraint fk_auth_session_member foreign key (member_id) references member (member_id)
) engine = InnoDB;

create index idx_auth_session_member on auth_session (member_id);

-- 탈퇴 시 코드에서 찜을 먼저 지운 뒤 회원을 지운다.
create table favorite (
    favorite_id bigint       not null auto_increment primary key,
    member_id   bigint       not null,
    event_id    varchar(512) not null,
    title       varchar(300) not null,
    start_date  date,
    end_date    date,
    place       varchar(300),
    saved_at    datetime(6)  not null,
    constraint uk_favorite_member_event unique (member_id, event_id),
    constraint fk_favorite_member foreign key (member_id) references member (member_id)
) engine = InnoDB;

-- 탈퇴한 회원의 댓글은 남기고 닉네임만 비워 보여주므로 member_id에 FK를 두지 않는다.
create table event_comment (
    comment_id bigint        not null auto_increment primary key,
    event_id   varchar(512)  not null,
    member_id  bigint        not null,
    parent_id  bigint,
    content    varchar(1000) not null,
    created_at datetime(6)   not null,
    updated_at datetime(6)
) engine = InnoDB;

create index idx_event_comment_event on event_comment (event_id);
create index idx_event_comment_parent on event_comment (parent_id);

create table ai_summary (
    event_id   varchar(512) not null primary key,
    summary    text         not null,
    created_at datetime(6)  not null
) engine = InnoDB;

create table event_view (
    event_id   varchar(512) not null primary key,
    view_count int          not null,
    version    bigint
) engine = InnoDB;

-- 탈퇴 시 코드에서 코스를 먼저 지운 뒤 회원을 지운다.
create table course (
    course_id             bigint        not null auto_increment primary key,
    member_id             bigint        not null,
    title                 varchar(50)   not null,
    favorited             boolean       not null,
    favorited_at          datetime(6),
    share_id              varchar(64),
    content_version       bigint        not null,
    stop_count            int           not null,
    first_event_title     varchar(300),
    first_event_image_url varchar(1000),
    created_at            datetime(6)   not null,
    updated_at            datetime(6)   not null,
    constraint uk_course_share_id unique (share_id),
    constraint fk_course_member foreign key (member_id) references member (member_id)
) engine = InnoDB;

-- 카페·음식점은 Google 약관상 place_id만 저장하고, 행사는 저장 당시 스냅샷을 남긴다.
create table course_stop (
    course_stop_id   bigint        not null auto_increment primary key,
    course_id        bigint        not null,
    stop_order       int           not null,
    stop_type        varchar(20)   not null,
    event_id         varchar(512),
    place_id         varchar(255),
    event_title      varchar(300),
    event_category   varchar(100),
    event_district   varchar(100),
    event_place      varchar(300),
    event_start_date date,
    event_end_date   date,
    event_image_url  varchar(1000),
    event_latitude   double,
    event_longitude  double,
    constraint fk_course_stop_course foreign key (course_id) references course (course_id)
) engine = InnoDB;

-- 날짜는 Google 무료 사용량 기준인 미국 태평양 시간이다.
create table places_api_usage (
    call_date         date not null primary key,
    call_count        int  not null,
    photo_call_count  int  not null default 0,
    detail_call_count int  not null default 0
) engine = InnoDB;

-- 분·일·월 단위 호출 한도 카운터. version으로 동시 차감을 막는다.
create table places_quota_bucket (
    id           varchar(255) not null primary key,
    version      bigint,
    window_start bigint       not null,
    usage_count  int          not null
) engine = InnoDB;
