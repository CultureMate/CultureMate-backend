# API 명세

기준: 이 레포 코드. 주소 `http://localhost:8080`.

## 공통

- 날짜: `yyyy-MM-dd`
- `eventId`는 URL일 수 있음 → query에 `encodeURIComponent(eventId)` 한 번만
- 오류: `{ "code": "...", "message": "..." }`
- 세션 쿠키: `CULTUREMATE_SESSION` HttpOnly, 7일. 인증 fetch는 `credentials: 'include'`
- 찜 사용자: 헤더 `X-Client-Id` (최대 64자). 로그인 회원과 아직 이관 없음
- 서울시 원본은 30분 캐시. 행사 테이블 없음

| code | HTTP | 의미 |
|------|------|------|
| `INVALID_PARAM` | 400 | 날짜·페이지·필수값 |
| `UNAUTHORIZED` | 401 | 세션 없음/만료 |
| `FORBIDDEN` | 403 | 본인 댓글이 아님 |
| `NOT_FOUND` | 404 | 행사 또는 찜 없음 |
| `ALREADY_SAVED` | 409 | 같은 브라우저·같은 행사 중복 찜 |
| `UPSTREAM_UNAVAILABLE` | 502 | 서울시 API 실패 |
| `AUTH_NOT_CONFIGURED` | 503 | `KAKAO_REST_KEY` 없음 |
| `AI_UNAVAILABLE` | 503 | AI 소개문 생성 실패 |

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
    "imageUrl": "https://..."
  }]
}
```

`count`는 이번 배열 길이, `totalCount`는 필터 전체. 페이지 없으면 `page`/`size`는 `null`.  
원본 시작일 > 종료일이면 날짜 검색에서 제외하고, 필터 없는 목록·상세에서는 날짜를 빈 문자열로 줍니다. 화면은 「일정 확인 필요」.

### `GET /api/events/detail?eventId={encoded}`

목록 필드 + `fee`, `organization`, `originalUrl`, `viewCount`(없으면 `0`).  
슬래시 없는 ID만 `GET /api/events/{eventId}` 가능. URL형 ID는 반드시 query.

## 3. 카카오 로그인 · 서버 완료

프론트는 `start`로 **이동**만 합니다. 콜백은 카카오가 부릅니다.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| GET | `/api/auth/kakao/start` | 302 → 카카오. 키 없으면 503 |
| GET | `/api/auth/kakao/callback` | 성공 `/?login=success`, 취소 `/?login=cancelled` + 세션 쿠키 |
| GET | `/api/auth/me` | `{ memberId, nickname, residence }` / 401 |
| POST | `/api/auth/logout` | 204, 쿠키 삭제 |

Redirect URI: `http://localhost:8080/api/auth/kakao/callback`

```js
window.location.href = 'http://localhost:8080/api/auth/kakao/start'
const me = await fetch('/api/auth/me', { credentials: 'include' })
await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
```

카카오 오류: `KOE006` URI 불일치, `KOE010` Client Secret, `KOE320` 이미 쓴 인가 코드 → `start`부터 다시.

## 4. 관심 행사 · 완료

헤더 `X-Client-Id` 필수. 없으면 400. 로그인 불필요. 저장 시 서버가 행사 스냅샷을 채움.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| POST | `/api/favorites` body `{ "eventId" }` | `201` 스냅샷. 중복 `409 ALREADY_SAVED` |
| GET | `/api/favorites` | 배열. 선택 `month=yyyy-MM`이면 그달과 기간이 겹치는 것만 |
| DELETE | `/api/favorites?eventId={encoded}` | `204`. 없으면 404 |
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

응답 항목: `commentId`, `eventId`, `memberId`, `parentId`, `content`, `createdAt`, `updatedAt`.  
본인 댓글이 아니면 `403 FORBIDDEN`. 세션 없으면 `401`.

## 7. 조회수 · 완료

로그인 불필요. 상세 진입 시 FE가 호출. 없는 행사는 `404`.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| POST | `/api/events/views?eventId={encoded}` | `200` `{ "eventId", "viewCount" }` |
| POST | `/api/events/{eventId}/views` | 슬래시 없는 ID용 |

최초 호출은 0에서 시작해 +1(결과 1). 이후 호출마다 +1.  
상세 `GET /api/events/detail`의 `viewCount`는 저장된 값(없으면 `0`).

## 8. 마이페이지 · 완료

세션 쿠키 필요. 없으면 `401`.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| GET | `/api/auth/me` | `{ memberId, nickname, residence }` |
| PUT | `/api/auth/me` body `{ "nickname"?, "residence"? }` | `200` 수정된 정보 |
| DELETE | `/api/auth/me` | `204`. 세션·회원 삭제, 쿠키 만료 |

수정 시 값이 없거나 기존과 같으면 그 필드는 변경하지 않음. 둘 다 없으면 `400 INVALID_PARAM`. 닉네임·거주지는 50자 이하이고, 넘으면 `400 INVALID_PARAM`.

## 9. 홈 HOT / 근처 · 완료

로그인 불필요. `district` 쿼리는 보내지 않으며, 지금은 서울 전체 기준입니다.  
거주지(쿠키) 반영은 후속. `limit` 기본 6, 범위 1~30.

| 메서드 | 경로 | 결과 |
|--------|------|------|
| GET | `/api/main/hot-events?limit=6` | `200` `{ "events": [...] }` 조회수 내림차순 |
| GET | `/api/main/upcoming-events?limit=6` | `200` `{ "events": [...] }` 시작일 오름차순(종료되지 않은 행사) |

각 항목: `eventId`, `title`, `category`, `district`, `place`, `startDate`, `endDate`, `imageUrl`, `viewCount`, `dDay`(한국 날짜 기준, 시작일 없으면 `null`).

## 10. 아직 없음

Google Places.

## 로컬 확인

1. `GET /api/health`
2. `GET /api/events?district=마포구&page=0&size=5`
3. 첫 `eventId`로 `GET /api/events/detail?eventId=...`
4. `X-Client-Id`로 찜 POST → GET → DELETE
5. 카카오 설정 후 `start` → `/me` → logout
6. 로그인 후 댓글 POST → GET → PUT → DELETE
7. `POST /api/events/views?eventId=...` → 상세 `viewCount` 증가 확인
8. `GET /api/main/hot-events?limit=6` · `GET /api/main/upcoming-events?limit=6`
