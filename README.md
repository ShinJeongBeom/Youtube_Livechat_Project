# Youtube Livechat Project

YouTube 라이브 채팅 replay 데이터를 저장하고, 특정 사용자의 채팅만 필터링해서 Excel 파일로 내보내는 Spring Boot 프로젝트입니다.

## What It Does

1. `videoId`를 받아 YouTube 영상의 라이브 채팅 replay를 `.live_chat.json` 파일로 저장합니다.
2. 저장된 채팅 JSON을 읽어 전체 채팅 또는 특정 사용자 채팅을 필터링합니다.
3. 필터링 결과를 `.xlsx` Excel 파일로 저장합니다.

## Requirements

- Java 17
- MySQL
- yt-dlp

이 프로젝트는 라이브 채팅 다운로드에 `yt-dlp`를 사용합니다. macOS에서 설치 예시는 아래와 같습니다.

```bash
brew install yt-dlp
```

## Environment

프로젝트 루트에 `.env` 파일을 만들고 로컬 DB 정보를 입력합니다.

```properties
DB_URL=jdbc:mysql://localhost:3306/youtube_livechat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your-mysql-password
```

`.env`는 `.gitignore`에 포함되어 있어 GitHub에 올라가지 않습니다.

MySQL 데이터베이스가 없다면 먼저 생성합니다.

```sql
CREATE DATABASE youtube_livechat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## Run

```bash
./gradlew bootRun
```

기본 서버 주소는 아래와 같습니다.

```text
http://localhost:8080
```

## API

### Download Live Chat

YouTube 라이브 채팅 replay를 `{videoId}.live_chat.json` 파일로 저장합니다.

```http
GET /api/chat/download?videoId=VIDEO_ID
```

예시:

```text
http://localhost:8080/api/chat/download?videoId=EpZTqvUymgM
```

### Filter Chat

저장된 라이브 채팅에서 특정 사용자의 댓글을 JSON으로 확인합니다.

```http
GET /api/chat/filter?videoId=VIDEO_ID&username=USERNAME
```

`username`을 생략하면 전체 채팅을 반환합니다.

```text
http://localhost:8080/api/chat/filter?videoId=EpZTqvUymgM&username=(사용자이름)
```

### Export To Excel

라이브 채팅을 필터링한 뒤 Excel 파일로 저장합니다.

```http
GET /api/chat/filter/excel?videoId=VIDEO_ID&username=USERNAME
```

`username`을 생략하면 전체 채팅을 Excel로 저장합니다.

```text
http://localhost:8080/api/chat/filter/excel?videoId=EpZTqvUymgM&username=(사용자이름)
```

## Output Files

라이브 채팅 JSON은 프로젝트 루트에 저장됩니다.

```text
VIDEO_ID.live_chat.json
```

Excel 파일은 `output/` 디렉터리에 저장됩니다.

```text
output/VIDEO_ID_USERNAME_filtered_chat.xlsx
```

## Notes

- `videoId`는 YouTube URL 전체가 아니라 `watch?v=` 뒤의 영상 ID입니다.
- 이미 `.live_chat.json` 파일이 있으면 다시 다운로드하지 않고 기존 파일을 사용합니다.
- 라이브 채팅 replay가 없는 영상은 다운로드에 실패할 수 있습니다.
