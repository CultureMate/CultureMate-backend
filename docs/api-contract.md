# API 명세

기준: 이 레포 코드. 주소 `http://localhost:8080`.

## 공통

- 날짜: `yyyy-MM-dd`
- `eventId`는 URL일 수 있음 → query에 `encodeURIComponent(eventId)` 한 번만
- 오류: `{ "code": "...", "message": "..." }`
- 세션 쿠키: `CULTUREMATE_SESSION` HttpOnly, 7일. 인증 fetch는 `credentials: 'include'`
- 관심 행사(찜)는 로그인 회원 기준. 세션 쿠키 없으면 `401`
- 서울시 원본은 30분 캐시. 행사 테이블 없음
- 원본 정리 규칙: 같은 행사가 여러 번 등록된 경우(제목·장소의 공백 제거·영문 소문자 통일 + 시작일이 같음, 종료일은 비교하지 않음) 하나로 합친다. 내용(이미지·기간·장소 등)은 등록일(`RGSTDATE`)이 가장 최신인 행을, 대표 `eventId`는 가장 먼저 등록된 행의 주소를 쓴다. 상세·조회수·댓글·찜·소개문은 저장·조회 전에 대표 `eventId`로 맞춘다. 합치기 전에 별칭 주소로 저장된 댓글·찜·소개문·조회수도 대표 행사에서 함께 본다

| code | HTTP | 의미 |
|------|------|------|
| `INVALID_PARAM` | 400 | 날짜·페이지·필수값, 파라미터 누락·형식 오류(숫자 변환 실패 포함) |
| `UNAUTHORIZED` | 401 | 세션 없음/만료 |
| `FORBIDDEN` | 403 | 본인 댓글/코스가 아님 |
| `NOT_FOUND` | 404 | 행사 또는 찜 없음 |
| `ALREADY_SAVED` | 409 | 같은 회원·같은 행사 중복 찜 |
| `COURSE_VERSION_CONFLICT` | 409 | 코스가 다른 곳에서 먼저 수정됨. 최신 상세를 다시 조회한 뒤 재시도 |
| `COURSE_LIMIT_EXCEEDED` | 409 | 회원당 저장 가능한 코스(50개) 초과. 기존 코스를 삭제한 뒤 재시도 |
| `UPSTREAM_UNAVAILABLE` | 502 | 서울시 API 실패 |
| `AUTH_NOT_CONFIGURED` | 503 | `KAKAO_REST_KEY` 없음 |
| `AI_UNAVAILABLE` | 503 | AI 소개문 생성 실패 |
| `PLACES_UNAVAILABLE` | 503 | Google Places 조회 실패(키 미설정 포함) |
| `PLACE_NOT_FOUND` | 404 | 장소 상세 조회 시 Google에 해당 장소가 없음(폐업·삭제 등). 코스 화면에서 "없어진 장소"로 안내 |
| `PLACES_RATE_LIMITED` | 429 | 같은 회원의 Places 1분 호출 한도 초과(검색 20회·사진 40회·상세 20회). 약 1분 뒤 재시도 |
| `PLACES_MEMBER_DAILY_LIMITED` | 429 | 같은 회원의 Places 일일 호출 한도 초과(기본 검색·사진·상세 각 20회) |
| `PLACES_QUOTA_EXCEEDED` | 503 | 서버 전체 Places 일일(검색 80·사진 60·상세 80) 또는 월간(각 900) 한도 초과 |
| `PLACES_QUOTA_BUSY` | 503 | 호출량 기록 충돌이 반복되어 처리하지 못함. 잠시 후 재시도 |
| `SHARE_ID_UNAVAILABLE` | 503 | 코스 공유 ID 생성 실패. 잠시 후 재시도 |

## 1. 헬스 · 완료

`GET /api/health` → `200` `{ "status": "UP" }`

## 2. 행사 · 완료

### `GET /api/events`

선택 query: `district`, `category`, `date`, `keyword`, `from`, `to`, `page`, `size`.  
같은 이름 반복은 OR, 다른 필드끼리는 AND.

| 파라미터 | 의미 |
|----------|------|
| `date` | 그날이 행사 기간에 포함 |
| `from` / `to` | 검색 기간과 행사 기간이 **겹침** |
| `keyword` | 제목 또는 장소 |
| `page` | 0부터. `page` 또는 `size`가 있으면 페이지 모드 |
| `size` | 1~100. page만 있으면 20, size만 있으면 page=0 |

```
GET /api/events?district=마포구&category=전시&from=2026-09-21&to=2026-09-30&page=0&size=20
```

```json
{
  "count": 1,
  "totalCount": 1,
  "page": 0,
  "size": 20,
  "events": [{
    "eventId": "https://culture.seoul.go.kr/...",
    "title": "행사명",
    "category": "전시/미술",
    "district": "마포구",
    "place": "행사장",
    "startDate": "2026-09-20",
    "endDate": "2026-09-25",
    "imageUrl": "https://...",
    "latitude": 37.55,
    "longitude": 126.91,
    "viewCount": 17
  }]
}
```

`count`는 이번 배열 길이, `totalCount`는 필터 전체. 페이지 없으면 `page`/`size`는 `null`.  
`viewCount`는 대표 ID와 별칭 ID에 저장된 조회수의 합이며 기록이 없으면 `0`. 목록 조회 자체는 조회수를 증가시키지 않음.
`latitude`·`longitude`는 서울시 원본 `LAT`·`LOT`. 값 앞쪽 숫자만 읽고(예: `37.57~2` → `37.57`), 뒤바뀐 행은 서울 범위로 바로잡음. 없거나 서울 범위 밖이면 `null`(온라인 행사 등). FE는 좌표가 없으면 장소명 검색 또는 위치 정보 없음으로 처리.  
원본 시작일 > 종료일이면 날짜 검색에서 제외하고, 필터 없는 목록·상세에서는 날짜를 빈 문자열로 줍니다. 화면은 「일정 확인 필요」.

### `GET /api/events/detail?eventId={encoded}`

목록 필드 + `fee`, `organization`, `originalUrl`, `viewCount`(없으면 `0`), `summary`(아직 생성 전이면 `null`).  
`summary`는 `POST /api/events/summary`로 이미 생성된 소개문이 있을 때만 채워지며, 이 API 자체는 새로 생성하지 않습니다.  
슬래시 없는 ID만 `GET /api/events/{eventId}` 가능. URL형 ID는 반드시 query.

## 3. 카카오 로그인 · 서버 완료

프론트는 `start`로 **이동**만 합니다. 콜백은 카카오가 부릅니다.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| GET | `/api/auth/kakao/start` | 302 → 카카오. 키 없으면 503 |
| GET | `/api/auth/kakao/callback` | 성공 `/?login=success`, 취소 `/?login=cancelled` + 세션 쿠키 |
| GET | `/api/auth/me` | 8번 마이페이지 참고. 비로그인은 `204`(본문 없음) |
| POST | `/api/auth/logout` | 204, 쿠키 삭제 |

Redirect URI: `http://localhost:8080/api/auth/kakao/callback`

카카오 닉네임(동의하지 않았으면 `카카오 사용자`)은 처음 가입할 때만 회원 닉네임으로 씁니다. 이후 로그인에서는 회원이 설정한 닉네임을 덮어쓰지 않습니다.

```js
window.location.href = 'http://localhost:8080/api/auth/kakao/start'
const me = await fetch('/api/auth/me', { credentials: 'include' })
await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
```

카카오 오류: `KOE006` URI 불일치, `KOE010` Client Secret, `KOE320` 이미 쓴 인가 코드 → `start`부터 다시.

## 4. 관심 행사 · 완료

세션 쿠키 필요(로그인 회원 ID 기준). 없으면 `401`. 저장 시 서버가 행사 스냅샷을 채움.  
다른 회원의 저장분은 조회되지 않고, 삭제하면 `404`.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| POST | `/api/favorites` body `{ "eventId" }` | `201` 스냅샷. 중복 `409 ALREADY_SAVED` |
| GET | `/api/favorites` | 배열. 선택 `month=yyyy-MM`이면 그달과 기간이 겹치는 것만. 대표 `eventId`당 한 건 |
| DELETE | `/api/favorites?eventId={encoded}` | `204`. 없으면 404. 대표 ID와 별칭 ID의 찜을 함께 삭제 |
| DELETE | `/api/favorites/{eventId}` | 슬래시 없는 ID용 |

응답 항목: `eventId`, `title`, `startDate`, `endDate`, `place`, `savedAt`. 날짜가 비정상이면 `null`일 수 있음.

## 5. AI 소개문 · 완료

행사 상세 정보를 바탕으로 소개문을 생성합니다. 저장된 소개문이 있으면 재사용합니다.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| POST | `/api/events/summary?eventId={encoded}` | `200` `{ "eventId", "summary", "createdAt" }` |
| POST | `/api/events/{eventId}/summary` | 슬래시 없는 ID용 |

저장본이 있으면 그대로 반환하고, 없으면 OpenAI로 생성 후 저장합니다.
생성 실패(키 미설정, 호출 실패 등)는 `AI_UNAVAILABLE` `503`.
OpenAI 읽기 제한은 30초, 출력은 최대 300토큰입니다. 같은 행사를 동시에 요청하면 한 번만 생성하고, 나머지는 저장된 소개문을 반환합니다.

## 6. 댓글 · 완료

작성·수정·삭제는 세션 쿠키 필요. 목록 조회는 공개.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| POST | `/api/comments` body `{ "eventId", "content", "parentId?" }` | `201` |
| GET | `/api/comments?eventId={encoded}` | 배열 (오래된 순) |
| PUT | `/api/comments/{commentId}` body `{ "content" }` | `200` |
| DELETE | `/api/comments/{commentId}` | `204` |

응답 항목: `commentId`, `eventId`, `memberId`, `nickname`, `parentId`, `content`, `createdAt`, `updatedAt`.  
`nickname`이 `null`이면 탈퇴한 회원. 대댓글은 중첩하지 않고 같은 배열에 `parentId`로 연결.  
본인 댓글이 아니면 `403 FORBIDDEN`. 세션 없으면 `401`. 상위 댓글을 삭제하면 대댓글도 함께 삭제.

## 7. 조회수 · 완료

로그인 불필요. 상세 진입 시 FE가 호출. 없는 행사는 `404`.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| POST | `/api/events/views?eventId={encoded}` | `200` `{ "eventId", "viewCount" }` |
| POST | `/api/events/{eventId}/views` | 슬래시 없는 ID용 |

최초 호출은 0에서 시작해 +1(결과 1). 이후 호출마다 +1.  
상세 `GET /api/events/detail`의 `viewCount`는 저장된 값(없으면 `0`).

## 8. 마이페이지 · 완료

세션 쿠키 필요. 없으면 `401`. 단, `GET /api/auth/me`는 로그인 여부 확인에 쓰므로 세션이 없거나 만료면 `204`(본문 없음)로 응답합니다.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| GET | `/api/auth/me` | `{ memberId, nickname, residence, interestCategories, favoriteCount }`. 비로그인 `204` |
| PUT | `/api/auth/me` body `{ "nickname"?, "residence"?, "interestCategories"? }` | `200` 수정된 정보(GET과 같은 형태) |
| DELETE | `/api/auth/me` | `204`. 카카오 연결 끊기 후 세션·관심 행사·회원 삭제, 쿠키 만료 |

수정 시 값이 없거나 기존과 같으면 그 필드는 변경하지 않음. 세 값이 모두 없으면 `400 INVALID_PARAM`. 닉네임·거주지는 50자 이하이고, 넘으면 `400 INVALID_PARAM`.  
`interestCategories`는 문자열 배열(예: `["전시","공연"]`). 보내면 통째로 교체, 공백·중복은 제거, 최대 10개, 값에 쉼표 불가. 쉼표로 이은 전체는 200자 이내이고, 넘으면 `400 INVALID_PARAM`.  
`residence`가 없으면 FE는 최초 로그인으로 보고 프로필 설정 화면으로 보냄.  
탈퇴한 회원의 댓글은 남고 `nickname`이 `null`로 내려감.  
탈퇴 시 `KAKAO_ADMIN_KEY`로 카카오 앱 연결을 끊어, 같은 카카오 계정으로 다시 로그인하면 동의 화면부터 나옴. 키가 없거나 카카오 호출이 실패해도 탈퇴는 `204`로 끝남(로그만 남김).

## 9. 홈 HOT / 근처 · 완료

로그인 불필요. `limit` 기본 6, 범위 1~30.  
upcoming의 자치구: `district` 쿼리 → 없으면 로그인 회원의 거주지 → 둘 다 없으면 서울 전체.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| GET | `/api/main/hot-events?limit=6` | `200` `{ "events": [...] }` 조회수 내림차순(같으면 제목순). 종료일이 오늘 이전인 행사와 기간 정보가 없는 행사는 제외. `viewCount`는 대표 ID와 별칭 ID의 합 |
| GET | `/api/main/upcoming-events?district=&limit=6` | `200` `{ "events": [...], "district": "마포구" 또는 null }` 오늘 이후 시작하는 행사, 시작일 오름차순 |

각 항목: `eventId`, `title`, `category`, `district`, `place`, `startDate`, `endDate`, `imageUrl`, `viewCount`, `dDay`(한국 날짜 기준, 시작일 없으면 `null`).

## 10. 장소 추천(Google Places) · 구현

행사 좌표 근처(또는 코스의 연속된 두 행사 사이)의 식당·카페 후보를 추천합니다. 추천 결과 자체는
서버에 저장하지 않고 매 호출마다 Google Places에서 조회합니다
(구글 약관상 이름·주소·평점·사진은 캐싱 금지). "다시 추천"과 "5개씩 페이지 넘기기"는 새로 호출하지
않고, 한 번에 받은 배열 안에서 프론트가 나눠 보여주는 방식을 전제로 합니다.

**중요**: Google Places Nearby Search(New)는 페이지네이션이 없어 한 번에 최대 20개까지만 받을 수
있습니다. 같은 좌표·조건으로 다시 호출해도 "다음 20개"가 오지 않고 사실상 같은 결과가 다시 옵니다.
"최대 20곳까지만 추천"이라는 점을 UI에 안내해야 합니다. 근처에 후보가 없거나, 있어도 전부 평점·리뷰
기준(평가 5개 이상)을 못 채우면 빈 배열 `[]`이며 에러가 아닙니다.

### 공통 규칙

`nearby`, `between`, `photo`는 로그인 세션 쿠키가 필요하며, 없거나 만료되면 `401 UNAUTHORIZED`입니다.
`details`는 공유 코스에서도 사용하므로 로그인 없이 호출할 수 있습니다.

**요청값 검증**: 아래 조건에 어긋나면 Google 호출과 호출량 차감 없이 `400 INVALID_PARAM`입니다.
필수 파라미터를 빼거나 숫자 자리에 글자를 넣은 경우도 `400 INVALID_PARAM`입니다.

| 값 | 조건 |
|----|------|
| `latitude`, `longitude` | NaN·무한대 불가, 국내 범위(위도 33~39, 경도 124~132) |
| `radius` | 1~50000(m) |
| `types` | Google Place Types **Table A**의 정확한 소문자 이름(공백 없이), 최대 50개. `food`·`establishment` 같은 Table B 값은 거절 |
| `/between`의 `type` | `cafe` 또는 `restaurant`(소문자) 하나 |
| `/photo`의 `name` | `places/{placeId}/photos/{photoId}` 형식. 식별자는 영문·숫자·`_`·`-`만. 쿼리·추가 경로·URL은 거절 |

**호출 제한**

| 구분 | 검색(`nearby` + `between`) | 사진(`photo`) | 상세(`details`) | 초과 시 |
|------|------:|------:|------:|------|
| 같은 회원, 1분 | 20회 | 40회 | 20회 | `429 PLACES_RATE_LIMITED` |
| 같은 회원, 하루 | 20회 | 20회 | 20회 | `429 PLACES_MEMBER_DAILY_LIMITED` |
| 서버 전체, 하루 | 80회 | 60회 | 80회 | `503 PLACES_QUOTA_EXCEEDED` |
| 서버 전체, 한 달 | 900회 | 900회 | 900회 | `503 PLACES_QUOTA_EXCEEDED` |

- 실제로 Google에 요청이 나가는 호출만 셉니다. 검증에 실패한 요청은 세지 않고, 한도에 걸린 요청은
  어떤 카운트도 차감하지 않습니다.
- 검색·사진·상세 모두 로그인이 필요하고, 제한 기준은 로그인 회원 ID입니다. Docker/Nginx 뒤에서도
  회원마다 별도로 집계됩니다. 1분 제한은 정각 분 단위 고정 구간입니다.
- 하루·한 달 경계는 미국 태평양 시간 기준입니다(구글 무료 사용량 갱신 시점). 한국 시간으로는 하루가
  오후 4~5시경(서머타임 여부에 따라)에 바뀝니다.
- `PLACES_RATE_LIMITED`는 약 1분 뒤에, 회원 일일 제한은 다음 날에 풀립니다.
  `503 PLACES_QUOTA_EXCEEDED`는 서버 전체 기준이라 다음 날 또는 다음 달에 풀립니다.
- 월간·분당·개별 일일 값은 환경변수(`PLACES_SEARCH_MONTHLY_LIMIT`, `PLACES_PHOTO_MONTHLY_LIMIT`,
  `PLACES_DETAIL_MONTHLY_LIMIT`, `PLACES_SEARCH_PER_MINUTE`, `PLACES_PHOTO_PER_MINUTE`,
  `PLACES_DETAIL_PER_MINUTE`, `PLACES_SEARCH_PER_MEMBER_DAILY`, `PLACES_PHOTO_PER_MEMBER_DAILY`,
  `PLACES_DETAIL_PER_MEMBER_DAILY`)로 바꿀 수 있습니다. 서버 전체 하루 한도(검색 80·사진 60·상세 80)는
  코드 상수입니다.
- 코스 하나(행사 N개)를 채우면 검색 호출이 `(N-1) * 2`번 나갑니다(구간마다 카페·음식점 각 1회).

**Google Places 언어/지역 설정**

- Nearby Search 요청에는 `languageCode: "ko"`, `regionCode: "KR"`을 넣습니다.
- 장소명과 주소를 한국어/대한민국 기준으로 우선 받기 위한 설정입니다. 다만 Google 원천 데이터 자체에
  번역이 없거나 상호에 외국어가 포함된 경우 일부 외국어가 남을 수 있습니다.

**응답 필드(`nearby`, `between` 공통)**

| 필드 | 의미 |
|------|------|
| `placeId`, `name`, `address` | 구글 장소 ID, 이름, 주소 |
| `rating`, `userRatingCount` | 평점, 평가 참여 수(구글은 평점 수와 리뷰 수를 따로 주지 않음) |
| `latitude`, `longitude`, `mapUrl` | 좌표, 구글맵 링크 |
| `photoName` | 첫 번째 사진의 참조값. 사진이 없으면 `null`. `/photo`에 그대로 넘김 |
| `authorAttributions` | 그 사진의 저작자 **전체** 배열 `{ displayName, uri, photoUri }`. 출처가 없으면 `[]`, 개별 필드가 없으면 `null` |
| `businessStatus` | 영업 상태(`OPERATIONAL` 등). 영구 폐업·장기 휴업은 애초에 제외되어 내려오지 않음 |
| `openNow` | 지금 영업 중인지. 영업시간 정보가 없으면 `null`(모름, 폐업 아님) |
| `openingHours` | 요일별 정규 영업시간 문자열 배열. Google의 `regularOpeningHours.weekdayDescriptions`를 한국어로 전달하며, 정보가 없으면 `[]` |
| `detourMeters`, `recommendationScore` | `between`에서만 채워짐(우회 거리 m, 최종 추천 점수). `nearby`는 `null` |

사진 옆에는 `authorAttributions` 배열의 **모든 저작자**를 표시해야 합니다(구글 약관상 필수).
Google Places 결과는 목록으로 표시하고 `mapUrl`로 Google Maps를 여는 방식으로 사용합니다. 카카오맵에는
Google Places의 장소·좌표를 마커로 표시하지 않습니다.

### `GET /api/places/nearby`

행사 상세 페이지 보강 등, 좌표 하나로 검색할 때 사용합니다.

| 파라미터 | 필수 | 의미 |
|----------|------|------|
| `latitude` | O | 중심 좌표 위도 |
| `longitude` | O | 중심 좌표 경도 |
| `types` | X | 콤마로 구분된 Google Place 타입. 생략하면 `restaurant,cafe` |
| `radius` | X | 검색 반경(m). 생략하면 500 |
| `maxResults` | X | 최대 후보 수. 생략하면 5, 1~20 밖의 값은 그 범위로 보정 |

```
GET /api/places/nearby?latitude=37.5125&longitude=127.0269&types=restaurant,cafe&radius=500&maxResults=5
```

```json
[
  {
    "placeId": "ChIJ...",
    "name": "스페이스 카페",
    "address": "서울 강남구 ...",
    "rating": 4.3,
    "userRatingCount": 128,
    "latitude": 37.5127,
    "longitude": 127.0271,
    "mapUrl": "https://maps.google.com/?cid=...",
    "photoName": "places/ChIJ.../photos/AeI...",
    "authorAttributions": [
      { "displayName": "홍길동", "uri": "https://maps.google.com/maps/contrib/...", "photoUri": "https://lh3.googleusercontent.com/..." },
      { "displayName": "김철수", "uri": "https://maps.google.com/maps/contrib/...", "photoUri": "https://lh3.googleusercontent.com/..." }
    ],
    "businessStatus": "OPERATIONAL",
    "openNow": true,
    "openingHours": [
      "월요일: 오전 10:00~오후 10:00",
      "화요일: 오전 10:00~오후 10:00",
      "수요일: 휴무"
    ],
    "detourMeters": null,
    "recommendationScore": null
  }
]
```

정렬은 평점·평가 수를 합친 베이지안 점수 순입니다. 구글 Nearby Search에는 평점순 옵션이 없어 항상 20개를
받아 서버에서 직접 정렬한 뒤 `maxResults`개만 돌려줍니다. 평점이 없거나 평가 5개 미만, 영구 폐업·장기 휴업
장소는 제외합니다(단, 구글 데이터도 100% 실시간은 아니라 "가보니 폐업"을 완전히 막아주진 못하니 `openNow`를
UI에 같이 안내하세요).

### `GET /api/places/between`

코스에서 **연속된 두 행사 사이** 구간에 끼워 넣을 카페/음식점을 찾을 때 사용합니다. 두 행사 좌표의
평균을 중심으로, 둘 사이 직선거리(하버사인)의 절반을 반경으로 검색합니다(반경은 100m~50km로 보정).
카테고리 하나당 최대 20개를 순위대로 전부 돌려주며, 응답 형식은 `nearby`와 같습니다.

| 파라미터 | 필수 | 의미 |
|----------|------|------|
| `eventId1` | O | 코스에서 앞에 오는 행사 ID |
| `eventId2` | O | 코스에서 뒤에 오는 행사 ID |
| `type` | O | `cafe` 또는 `restaurant` (한 번에 하나만) |

```
GET /api/places/between?eventId1=...&eventId2=...&type=cafe
GET /api/places/between?eventId1=...&eventId2=...&type=restaurant
```

행사 N개짜리 코스 전체를 채우려면 연속된 쌍마다(행사1-행사2, 행사2-행사3, ...) 이 API를 `cafe`,
`restaurant`로 각각 호출합니다(총 `(N-1) * 2`번). 없는 행사는 `404 NOT_FOUND`, 두 행사 중 하나라도 좌표가
없거나 범위를 벗어나면 `400`입니다.

**추천 순위 계산**

1. 평점이 없거나 평가 수 5개 미만, 영구 폐업·장기 휴업, 좌표가 없거나 유효하지 않은 후보를 제외합니다.
2. 후보마다 **우회 거리** `D = max(0, 거리(행사1, 후보) + 거리(후보, 행사2) - 거리(행사1, 행사2))`를 구합니다.
3. **허용 우회 거리** `L = min(2000m, max(300m, 행사 간 직선거리 × 0.5))`를 넘는 후보를 제외합니다.
   (행사가 같은 곳이어도 최소 300m가 적용되어 0으로 나누지 않습니다. 행사 간 거리가 약 4.8km 이하면 검색
   범위 안의 후보는 모두 통과하고, 그보다 긴 구간에서만 실제로 잘립니다.)
4. 남은 후보의 평균 평점을 `C`, 후보의 평점을 `R`, 평가 수를 `v`라 하면
   `B = (v × R + 10 × C) / (v + 10)`(평가가 적을수록 평균 쪽으로 보정), **최종 점수 = `B - 0.5 × (D / L)`**.
   우회 때문에 깎이는 점수는 최대 0.5점이라 품질(평점·평가 수)이 순위를 좌우하되, 비슷한 품질이면 덜 돌아가는
   곳이 앞섭니다.
5. 점수 내림차순으로 정렬하고, 점수가 같으면 우회 거리, 그다음 `placeId` 순입니다.

예: `L = 1000m`일 때 후보 A(B 4.5, 우회 100m) → 4.45, 후보 B(B 4.8, 우회 400m) → 4.60,
후보 C(B 4.5, 우회 600m) → 4.20. 순서는 B → A → C입니다.

거리는 지표면상 **직선거리**의 추정값이라 실제 도보·차량 경로의 거리나 이동 시간이 아닙니다(강·다리·출입구
등은 반영하지 않음). `detourMeters`는 반올림하지 않은 소수 m이므로 화면에 보여줄 땐 반올림하세요.

### `GET /api/places/details`

저장된 코스에는 카페·음식점의 `placeId`만 보관하므로, 코스를 다시 열 때 최신 장소 정보를 가져오는 용도입니다.
로그인이 필요합니다(없으면 `401`). 공유 코스 화면도 로그인한 회원만 볼 수 있으므로 동일하게 사용합니다.

| 파라미터 | 필수 | 의미 |
|----------|------|------|
| `placeId` | O | 코스 스탑에 저장된 Google Place ID |

```
GET /api/places/details?placeId=ChIJ...
```

응답은 `nearby`와 같은 `PlaceCandidateDTO` 형식이며 `detourMeters`, `recommendationScore`는 `null`입니다.
이 호출은 검색·사진과 별도의 상세 조회 호출량을 사용하며, 같은 회원 기준 하루 20회까지입니다. 저장된 코스
카드 여러 개의 장소 정보를 한꺼번에 미리 요청하지 말고, 실제 상세 화면에서 필요한 스탑만 조회하세요. 같은
화면에서 이미 받은 장소 정보는 다시 요청하지 말고 재사용하세요.

**오류**: 비로그인 `401`, 형식이 틀리거나 Google이 거절한 ID는 `400 INVALID_PARAM`, 폐업·삭제된 장소는
`404 PLACE_NOT_FOUND`, 회원 한도 초과 `429`, 서버 전체 한도 초과·구글 장애·키 미설정은 `503`입니다.
`404`는 일시적인 오류가 아니므로 재시도하지 말고 "없어진 장소"로 표시하세요.

### `GET /api/places/photo`

`photoName`을 실제로 화면에 띄울 수 있는 이미지로 바꿉니다. **후보 전체가 아니라 실제로 보여줄 사진에
대해서만** 호출해야 합니다(검색과 별도 과금 SKU, 하루 60건 별도 한도). 목록의 카드 전부에 썸네일을 미리
깔지 말고, 사용자가 실제로 자세히 보거나 코스에 추가하려는 곳만 그때그때 불러오세요.

| 파라미터 | 필수 | 의미 |
|----------|------|------|
| `name` | O | 검색 응답의 `photoName` 값 그대로 |
| `maxWidthPx` | X | 원하는 이미지 최대 가로 픽셀. 생략하면 400, 1~1600 밖의 값은 그 범위로 보정 |

```
GET /api/places/photo?name=places/ChIJ.../photos/AeI...&maxWidthPx=400
```

응답은 이미지 자체가 아니라 **302 리다이렉트**(구글 CDN 링크로)입니다. 프론트에서 그냥
`<img src="/api/places/photo?name=...">`로 쓰면 되고, API 키는 서버 밖으로 나가지 않습니다.
같은 이미지를 다시 그릴 때마다 호출이 나가면 사진 한도와 회원별 제한을 빨리 쓰니, 한 번 받은 이미지는
프론트에서 재사용하세요. 302 응답에는 `Cache-Control: max-age=3600, private`이 붙어 있어, 1시간 안에 같은
주소(`name`·`maxWidthPx`가 같은 경우)를 다시 요청하면 브라우저가 서버를 거치지 않고 저장된 리다이렉트를 씁니다.
오류 응답(401·429·503 등)은 캐시되지 않습니다.

**프론트 구현 주의 — 사진은 lazy loading 하세요.**

- 카드 목록을 받았다고 모든 `photoName`을 즉시 `/api/places/photo`로 요청하지 않습니다.
- 현재 화면(viewport)에 들어온 카드의 사진만 요청하고, 아직 보이지 않는 카드는 스크롤되어 보일 때 요청합니다.
- 단순 이미지라면 `loading="lazy"`를 사용할 수 있고, 카드 컴포넌트에서 호출 시점을 직접 제어한다면
  `IntersectionObserver`로 화면 진입 시 `src`를 설정하는 방식을 권장합니다.
- 페이지 이동이나 재렌더링으로 같은 사진 URL을 반복 요청하지 않도록 이미 받은 결과를 재사용하세요.
- 사진 호출은 검색과 별도 한도를 사용하며 기본 제한은 같은 회원 기준 **분당 40회·하루 20회**입니다. 카드 수가
  많은 화면에서 eager loading을 하면 불필요하게 `429`에 도달할 수 있습니다.

**오류**: 비로그인 `401`, 형식이 틀리면 `400`, 회원 한도 초과 `429`(`PLACES_RATE_LIMITED` /
`PLACES_MEMBER_DAILY_LIMITED`), 서버 전체 한도 초과·구글 실패·키 미설정은 `503`
(`PLACES_QUOTA_EXCEEDED` / `PLACES_UNAVAILABLE`)입니다.

## 11. 코스 저장 · 구현

코스 편집(행사/장소 추가·삭제, 순서 변경, 제목 변경)은 프론트에서 처리하고, **저장 또는 수정 버튼을 누를 때만**
스탑 배열 전체를 서버로 보냅니다. 저장된 코스가 "내가 만든 코스" 목록이고, 그중 별표를 켠 것이
"즐겨찾기한 코스"입니다. 모든 소유자 API는 세션 쿠키가 필요하며, 다른 회원의 코스 ID에 접근하면 `403`입니다.

### 저장 규칙

- 제목: 공백을 제외하고 1자 이상, 최대 50자. 서버가 제목을 자동 생성하지 않습니다.
- 스탑: 1~20개, **행사 스탑이 최소 1개** 있어야 합니다. 같은 행사 또는 같은 장소를 두 번 넣을 수 없습니다.
- 코스 개수: 회원당 최대 50개. 초과하면 `409 COURSE_LIMIT_EXCEEDED`입니다.
- `type`: `event`, `cafe`, `restaurant`. `event`는 `eventId`, 나머지는 `placeId`만 보냅니다.
- 서버는 요청 배열 순서를 그대로 `stopOrder` 0부터 저장합니다.
- 행사 스탑은 저장 시 서버가 행사 상세를 다시 확인하고 제목·장소·기간·이미지·좌표 스냅샷을 저장합니다.
- 카페·음식점은 `placeId`만 저장합니다. 화면에 보여줄 최신 정보는 `/api/places/details`로 다시 조회합니다.
- 수정 시 기존 코스에 이미 있던 `eventId`는 저장된 스냅샷을 재사용하고, 새로 추가된 행사만 다시 조회합니다.
- `version`은 코스 내용 수정 때만 증가합니다. 별표/공유 토글은 `version`을 올리지 않습니다.

### `POST /api/courses` — 새 코스로 저장

```json
{
  "title": "성수 문화 산책",
  "stops": [
    { "type": "event", "eventId": "https://culture.seoul.go.kr/..." },
    { "type": "cafe", "placeId": "ChIJ..." },
    { "type": "restaurant", "placeId": "ChIJ..." }
  ]
}
```

`201`. 새 코스가 생성되며 초기 `version`은 `1`입니다. 기존 코스를 남기고 "새 코스로 저장"할 때도 이 API를 사용합니다.

### `PUT /api/courses/{courseId}` — 기존 코스 덮어쓰기

```json
{
  "title": "성수 문화 산책 수정본",
  "version": 1,
  "stops": [
    { "type": "event", "eventId": "https://culture.seoul.go.kr/..." },
    { "type": "cafe", "placeId": "ChIJ_NEW..." }
  ]
}
```

프론트가 마지막으로 받은 `version`을 그대로 보내야 합니다. 서버의 최신 버전과 다르면
`409 COURSE_VERSION_CONFLICT`이며, 최신 상세를 다시 불러온 뒤 사용자에게 재시도를 안내합니다. 성공하면 버전이 1 증가합니다.

### 조회·즐겨찾기·삭제

| 메서드 | 경로 | 결과 |
|--------|------|------|
| GET | `/api/courses` | 내가 만든 코스 최신 생성순 |
| GET | `/api/courses?favorite=true` | 별표 켠 코스의 `favoritedAt` 최신순 |
| GET | `/api/courses/{courseId}` | 저장된 코스 상세 |
| PUT | `/api/courses/{courseId}/favorite` body `{ "favorited": true/false }` | 별표 상태 설정(같은 값을 여러 번 보내도 동일) |
| DELETE | `/api/courses/{courseId}` | `204` |

목록 항목: `courseId`, `title`, `favorited`, `favoritedAt`, `version`, `stopCount`,
`firstEventTitle`, `firstEventImageUrl`, `previewStops`, `shared`, `createdAt`, `updatedAt`.
`firstEventTitle`, `firstEventImageUrl`은 기존 프론트 호환을 위해 유지합니다.

`previewStops`는 실제 코스 순서(`stopOrder`)대로 최대 4개를 반환합니다. 행사 스탑은 저장된 스냅샷의
`eventId`, `eventTitle`, `eventImageUrl`을 각각 `eventId`, `name`, `imageUrl`로 사용합니다. 카페·음식점 스탑은
목록 조회에서 Google Places를 추가 호출하지 않고 저장된 `placeId`만 반환하며, `name`, `imageUrl`은 `null`입니다.
장소 스탑은 프론트에서 타입별 기본 이미지 또는 아이콘으로 표시하고, 실제 장소명·사진은 코스 상세 등 필요한
시점에 `/api/places/details`, `/api/places/photo`로 조회합니다.

```json
{
  "courseId": 1,
  "title": "서울 문화 산책",
  "stopCount": 7,
  "previewStops": [
    {
      "stopOrder": 0,
      "type": "restaurant",
      "eventId": null,
      "placeId": "ChIJ_restaurant_1",
      "name": null,
      "imageUrl": null
    },
    {
      "stopOrder": 1,
      "type": "event",
      "eventId": "https://culture.seoul.go.kr/...",
      "placeId": null,
      "name": "행사 1",
      "imageUrl": "https://..."
    },
    {
      "stopOrder": 2,
      "type": "cafe",
      "eventId": null,
      "placeId": "ChIJ_cafe_1",
      "name": null,
      "imageUrl": null
    },
    {
      "stopOrder": 3,
      "type": "restaurant",
      "eventId": null,
      "placeId": "ChIJ_restaurant_2",
      "name": null,
      "imageUrl": null
    }
  ]
}
```

`stopCount`가 4보다 크면 프론트는 `stopCount - previewStops.length`를 `+N`으로 표시합니다. 예를 들어
전체 스탑이 7개이면 앞의 4개 스탑을 미리보기로 표시하고 `+3`을 붙입니다. `+N`은 사진을 가져오지 못했다는
뜻이 아니라 목록 카드에서 생략된 나머지 스탑 수입니다. 목록 미리보기 때문에 `photoName`을 저장하거나
Place Details/Photo를 미리 호출하지 않습니다.

상세에는 위 기본 정보와 `shareId`, `stops`가 포함됩니다. 각 스탑은 `stopOrder`, `type`, `eventId`, `placeId`와
행사인 경우 저장 당시의 `eventTitle`, `eventCategory`, `eventDistrict`, `eventPlace`, `eventStartDate`, `eventEndDate`,
`eventImageUrl`, `eventLatitude`, `eventLongitude`를 가집니다. 장소 스탑의 행사 필드는 `null`입니다.

### 공유 링크

기본은 비공개입니다. 공유를 켜면 백엔드는 추측하기 어려운 `shareId`만 만들고, 프론트가 자기 화면 경로와 합쳐
예: `/shared/courses/{shareId}` 링크를 만듭니다.

| 메서드 | 경로 | 로그인 | 결과 |
|--------|------|--------|------|
| POST | `/api/courses/{courseId}/share` | 필요 | `200 { "shareId": "..." }` (이미 켜져 있으면 같은 ID) |
| DELETE | `/api/courses/{courseId}/share` | 필요 | `204`, 기존 공유 링크 즉시 무효 |
| GET | `/api/courses/shared/{shareId}` | 필요 | 로그인한 회원이면 누구나 조회 가능. 제목·생성/수정일·스탑만 반환하고 소유자/별표 정보는 노출하지 않음 |

회원 탈퇴(`DELETE /api/auth/me`) 시 세션·관심행사와 함께 해당 회원의 코스/코스 스탑도 삭제됩니다.

행사가 1개인 코스도 저장할 수 있습니다. 코스를 구성할 때 주변 장소 탐색은 그 행사 좌표로
`/api/places/nearby`를 사용하고, 행사가 2개 이상이면 연속 행사 사이마다 `/api/places/between`을 사용합니다.

## 12. 아직 없음

상세 보완(댓글 외).

## 로컬 확인

1. `GET /api/health`
2. `GET /api/events?district=마포구&page=0&size=5`
3. 첫 `eventId`로 `GET /api/events/detail?eventId=...`
4. 로그인 후 찜 POST → GET → DELETE
5. 카카오 설정 후 `start` → `/me` → logout
6. 로그인 후 댓글 POST → GET → PUT → DELETE
7. `POST /api/events/views?eventId=...` → 상세 `viewCount` 증가 확인
8. `GET /api/main/hot-events?limit=6` · `GET /api/main/upcoming-events?limit=6`
9. 로그인 후 코스 POST → GET 목록/상세 → PUT(version 포함) → favorite → share → DELETE
10. 공유된 `shareId`로 다른 회원 로그인 상태에서 `GET /api/courses/shared/{shareId}` (로그아웃 상태면 `401`)
11. 로그인 후 저장된 장소 `placeId`로 `GET /api/places/details?placeId=...`
