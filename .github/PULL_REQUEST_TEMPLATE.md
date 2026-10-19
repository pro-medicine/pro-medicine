## 1. 변경 요약 (Summary)
- 작업 내용 및 구현한 기능을 간결하게 명사형으로 기술함.
  
## 2. 관련 이슈 (Related Issues)
- Resolves: #이슈번호
- Related to: #이슈번호
  
## 3. 세부 변경 사항 (Key Changes)
- [BE] JWT 토큰 발급 및 검증 필터 구현 (`JwtTokenProvider.java`)
- [BE] Spring Security WebSecurityCustomizer 정적 자원 예외 처리
- [FE] 로그인 입력 폼 컴포넌트 추가 (`src/pages/LoginPage.jsx`)
- [FE] 로그인 성공 시 Redux `authSlice` 토큰 저장 및 Axios 인터셉터 주입
  
## 4. 테스트 및 검증 결과 (Verification)
- [ ] Postman을 통한 `/api/v1/auth/login` 200 OK 응답 검증 완료
- [ ] 토큰 미포함 요청 시 401 Unauthorized 에러 응답 확인 완료
- [ ] 프론트엔드 브라우저 화면에서 정상 로그인 후 메인 대시보드 리다이렉트 확인 완료
      
## 5. 리뷰어 요청 사항 (To Reviewers)
- 토큰 만료 시간 설정(`expiration: 3600000`)의 적절성 검토를 요청함.
