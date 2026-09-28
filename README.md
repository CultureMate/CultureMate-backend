# 🎫 CultureMate · Spring Backend

서울시 문화행사를 날짜·분야·지역으로 찾고, 관심 행사로 모아 두는 Spring Boot API입니다. 화면은 [CultureMate-frontend](https://github.com/CultureMate/CultureMate-frontend)가 담당합니다.

> 🔒 비밀값은 코드나 Git에 넣지 않습니다. `.env.example`을 복사해 `.env`를 만들고 각 값을 채우세요.

<br>

## 📌 현재 범위

- ✅ **구현됨:** 서울시 문화행사 목록·상세·필터, 관심 행사(찜), 카카오 로그인·세션, 댓글, 조회수, 홈 HOT/근처, 로컬 H2, AI 소개문
- 📋 **이슈로 남김:** 마이페이지 수정·탈퇴, 상세 보완, Google Places, Docker DB

<br>

## 📚 문서 안내

문서마다 한 가지 기준만 둡니다.

- 🔗 [API 계약](./docs/api-contract.md): URL, 요청·응답, 오류 코드
- 🗂️ ERD: ERDCloud가 원본. 로컬은 H2 + JPA `ddl-auto: update`

<br>

## ⚙️ 동작 원칙

- 행사 원본은 DB에 저장하지 않습니다. 서울시 API를 호출하고 30분 캐시합니다.
- 찜(관심 행사)은 로그인 회원 기준입니다. 세션 쿠키가 없으면 `401`입니다.
- 카카오 로그인은 `CULTUREMATE_SESSION` HttpOnly 쿠키(7일)입니다. `/me`·로그아웃은 `credentials: include`가 필요합니다.
- API 키는 `.env`의 `SEOUL_API_KEY`, `KAKAO_REST_KEY`만 읽습니다.

<br>

## 🚀 실행

JDK **17**. Maven은 `mvnw`가 받습니다.

```bash
copy .env.example .env
```

macOS/Linux는 `cp .env.example .env` 입니다. `.env`에 `SEOUL_API_KEY`를 넣습니다. 카카오를 쓰려면 `KAKAO_REST_KEY`와 콘솔 Redirect URI `http://localhost:8080/api/auth/kakao/callback`도 맞춥니다. 탈퇴 시 카카오 연결까지 끊으려면 콘솔 "앱 키 > 어드민 키"를 `KAKAO_ADMIN_KEY`에 넣습니다(서버 `.env`에만 보관).

```bash
.\mvnw.cmd spring-boot:run
```

macOS/Linux는 `./mvnw spring-boot:run` 입니다. 터미널만 있어도 됩니다. IDE를 쓰면 이 폴더를 프로젝트로 열고, Working directory가 `.env`가 있는 루트여야 합니다.

- 🖥️ API: http://localhost:8080
- ❤️ 헬스: http://localhost:8080/api/health
- 📅 목록: http://localhost:8080/api/events?page=0&size=5
- 📱 화면: http://localhost:3000 (`localhost`로 엽니다. `127.0.0.1`이면 카카오 쿠키가 어긋날 수 있습니다)

키가 비면 목록은 `502`, 카카오 시작은 `503`입니다. `.env` · `data/` · `target/` 은 Git에 올리지 않습니다.

### 🐬 MariaDB로 실행 (docker 프로필)

기본 실행은 위처럼 H2입니다. MariaDB는 Docker로 띄우고 `docker` 프로필일 때만 씁니다.

```bash
docker compose up -d db
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=docker"
```

`.env`에 `DB_PASSWORD`가 있어야 합니다. 테이블은 Flyway(`src/main/resources/db/migration`)가 만들고, Hibernate는 `validate`로 검사만 합니다.
엔티티를 바꾸면 기존 SQL은 고치지 말고 `V2__설명.sql`처럼 다음 버전 파일을 함께 추가합니다. 로컬 H2는 그대로 돌아가도 MariaDB에서는 서버가 뜨지 않습니다.

- DB 도구 접속(HeidiSQL 등): `MariaDB or MySQL (TCP/IP)` / `127.0.0.1:3308` / DB `culturemate` / 사용자 `culturemate` / 암호 `DB_PASSWORD`
- PC에 설치된 MariaDB·MySQL이 3306을 쓰는 경우가 많아 기본 포트를 `3308`로 뒀습니다. 바꾸려면 `.env`의 `DB_PORT`를 고칩니다.
- 끄기: `docker compose down` (데이터 유지) · 초기화: `docker compose down -v`

<br>

## 🧪 테스트

```bash
.\mvnw.cmd test
```
