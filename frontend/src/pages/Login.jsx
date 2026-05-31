const BACKEND = 'http://localhost:8080'

export default function Login() {
  return (
    <div style={styles.container}>
      <div style={styles.hero}>
        <div style={styles.logo}>👗</div>
        <h1 style={styles.title}>내 옷장</h1>
        <p style={styles.subtitle}>AI가 추천하는 오늘의 코디</p>
      </div>

      <div style={styles.buttons}>
        <a href={`${BACKEND}/oauth2/authorization/google`} style={styles.googleBtn}>
          <img
            src="https://www.google.com/favicon.ico"
            alt="Google"
            style={{ width: 20, height: 20 }}
          />
          Google로 계속하기
        </a>

        <a href={`${BACKEND}/oauth2/authorization/kakao`} style={styles.kakaoBtn}>
          <span style={{ fontSize: 20 }}>💬</span>
          카카오로 계속하기
        </a>
      </div>
    </div>
  )
}

const styles = {
  container: {
    minHeight: '100vh',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    padding: '40px 32px',
    background: 'white',
  },
  hero: {
    textAlign: 'center',
    marginBottom: 60,
  },
  logo: {
    fontSize: 72,
    marginBottom: 16,
  },
  title: {
    fontSize: 32,
    fontWeight: 800,
    color: '#1a1a1a',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#888',
  },
  buttons: {
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  googleBtn: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    padding: '15px 24px',
    borderRadius: 12,
    border: '1.5px solid #e0e0e0',
    background: 'white',
    color: '#1a1a1a',
    fontWeight: 600,
    fontSize: 15,
    textDecoration: 'none',
    cursor: 'pointer',
  },
  kakaoBtn: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    padding: '15px 24px',
    borderRadius: 12,
    border: 'none',
    background: '#FEE500',
    color: '#1a1a1a',
    fontWeight: 600,
    fontSize: 15,
    textDecoration: 'none',
    cursor: 'pointer',
  },
}
