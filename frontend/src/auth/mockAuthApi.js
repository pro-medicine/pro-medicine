/**
 * 개발 전용 인증 Mock. 실제 로그인 검증이나 개인정보 보관에 사용하지 말 것.
 * schema.sql의 가상 샘플 사용자 5명을 기본 계정으로 사용한다.
 */
const USERS_KEY = "yakssok.dev.mockUsers.v1";
const SESSION_KEY = "yakssok.dev.mockSession.v1";
const REMEMBER_KEY = "yakssok.dev.mockRemember.v1";

const SEED_USERS = [
  { userId: 1, email: "minji@example.com", nickname: "김민지", shareIntakeStatus: true, createdAt: "2026-10-01T09:00:00" },
  { userId: 2, email: "seojun@example.com", nickname: "김서준", shareIntakeStatus: true, createdAt: "2026-10-02T10:00:00" },
  { userId: 3, email: "yujin@example.com", nickname: "이유진", shareIntakeStatus: true, createdAt: "2026-10-03T11:00:00" },
  { userId: 4, email: "jiho@example.com", nickname: "박지호", shareIntakeStatus: true, createdAt: "2026-10-04T12:00:00" },
  { userId: 5, email: "eunseo@example.com", nickname: "최은서", shareIntakeStatus: false, createdAt: "2026-10-05T13:00:00" },
];
const DEMO_PASSWORD = "password1234!";
let initializePromise;

const encodeHex = (buffer) => Array.from(new Uint8Array(buffer))
  .map((byte) => byte.toString(16).padStart(2, "0")).join("");

function randomSalt() {
  return encodeHex(crypto.getRandomValues(new Uint8Array(16)));
}

async function hashPassword(password, saltHex) {
  const salt = Uint8Array.from(
    saltHex.match(/.{2}/g),
    (part) => Number.parseInt(part, 16)
  );
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]
  );
  const digest = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: 120000 },
    key, 256
  );
  return encodeHex(digest);
}

async function makeRecord({ userId, email, nickname, password, age = null, gender = null, shareIntakeStatus = false, createdAt = null }) {
  const salt = randomSalt();
  return {
    userId,
    email: email.trim().toLowerCase(),
    nickname: nickname.trim(),
    age,
    gender,
    shareIntakeStatus,
    salt,
    passwordHash: await hashPassword(password, salt),
    createdAt: createdAt ?? new Date().toISOString(),
  };
}

async function initialize() {
  if (!initializePromise) {
    initializePromise = (async () => {
      if (localStorage.getItem(USERS_KEY) !== null) return;
      const seeds = await Promise.all(SEED_USERS.map((user) =>
        makeRecord({ ...user, password: DEMO_PASSWORD })
      ));
      if (localStorage.getItem(USERS_KEY) === null) {
        localStorage.setItem(USERS_KEY, JSON.stringify(seeds));
      }
    })();
  }
  return initializePromise;
}

function users() {
  try {
    const parsed = JSON.parse(localStorage.getItem(USERS_KEY) ?? "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function publicUser(user) {
  return {
    userId: user.userId,
    email: user.email,
    nickname: user.nickname,
    shareIntakeStatus: user.shareIntakeStatus ?? false,
    createdAt: user.createdAt,
  };
}

function signIn(userId, rememberMe = false) {
  localStorage.removeItem(REMEMBER_KEY);
  sessionStorage.removeItem(SESSION_KEY);
  (rememberMe ? localStorage : sessionStorage).setItem(
    rememberMe ? REMEMBER_KEY : SESSION_KEY,
    String(userId)
  );
}

export async function signup(data) {
  await initialize();
  const email = data.email?.trim().toLowerCase();
  const nickname = data.name?.trim();
  const age = Number(data.age);
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
      !nickname || nickname.length > 50 ||
      !/^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$/.test(data.password ?? "") ||
      !Number.isInteger(age) || age < 1 || age > 120 ||
      !["여성", "남성", "선택 안 함"].includes(data.gender) ||
      data.termsAgreed !== true) {
    throw new Error("회원가입 입력값을 확인해주세요.");
  }
  const existing = users();
  if (existing.some((u) => u.email === email)) throw new Error("이미 가입된 이메일입니다.");
  if (existing.some((u) => u.nickname === nickname)) throw new Error("이미 사용 중인 닉네임입니다.");
  const userId = Math.max(0, ...existing.map((u) => u.userId)) + 1;
  const record = await makeRecord({ userId, email, nickname, password: data.password, age, gender: data.gender });
  localStorage.setItem(USERS_KEY, JSON.stringify([...existing, record]));
  signIn(userId);
  return publicUser(record);
}

export async function login({ email, password, rememberMe = false }) {
  await initialize();
  const account = users().find((user) => user.email === email?.trim().toLowerCase());
  if (!account || await hashPassword(password ?? "", account.salt) !== account.passwordHash) {
    throw new Error("이메일 또는 비밀번호가 올바르지 않습니다.");
  }
  signIn(account.userId, rememberMe);
  return publicUser(account);
}

export async function getCurrentUser() {
  await initialize();
  const id = localStorage.getItem(REMEMBER_KEY) ?? sessionStorage.getItem(SESSION_KEY);
  if (!id) return null;
  const account = users().find((user) => user.userId === Number(id));
  return account ? publicUser(account) : null;
}

export async function logout() {
  sessionStorage.removeItem(SESSION_KEY);
  localStorage.removeItem(REMEMBER_KEY);
}
