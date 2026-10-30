import { useEffect, useState } from "react";
import { TopBar } from "../shared/ui";
import {
  fetchConditions,
  fetchHealthProfile,
  fetchUser,
  saveConditions,
  saveHealthProfile,
  updateNickname,
} from "../api/mypageApi";

// 선택지 값은 설문(STEP02_seed_survey_v1.sql)의 value_code와 같게 맞춤
// 설문에서 저장한 값과 편집에서 저장한 값이 같아야 AI 추천도 같은 기준으로 읽는다
const choiceFields = [
  {
    key: "smoking",
    label: "흡연",
    options: [
      ["NONE", "안 피움"],
      ["HALF_PACK", "하루 반 갑 이하"],
      ["ONE_PACK", "하루 1갑"],
      ["TWO_PACK_PLUS", "하루 2갑 이상"],
    ],
  },
  {
    key: "drinking",
    label: "음주",
    options: [
      ["NONE", "안 마심"],
      ["LOW", "가끔"],
      ["HIGH", "자주"],
    ],
  },
  {
    key: "caffeine",
    label: "커피·차",
    options: [
      ["NONE", "안 마심"],
      ["LOW", "하루 1~2잔"],
      ["HIGH", "하루 3잔 이상"],
    ],
  },
  {
    key: "dietType",
    label: "식단",
    options: [
      ["OMNI", "일반식"],
      ["VEGETARIAN", "채식(유제품·달걀 포함)"],
      ["VEGAN", "비건"],
    ],
  },
  {
    key: "sunExposure",
    label: "햇빛 노출",
    options: [
      ["LOW", "거의 실내에 있어요"],
      ["NORMAL", "보통이에요"],
    ],
  },
];

// 질환 선택지: [설문 value_code, 화면 문구]
// 설문의 "없음"(NONE)은 빈 목록으로 저장되므로 여기서는 뺀다 (질환을 모두 지우면 "없음")
const conditionOptions = [
  ["HYPERTENSION", "고혈압"],
  ["DIABETES", "당뇨"],
  ["THYROID", "갑상선 질환"],
  ["KIDNEY", "신장 질환"],
];

// 질환 코드 → 화면 문구 (예전에 한글 이름으로 저장된 값은 그대로 보여줌)
function conditionLabel(code) {
  for (const [value, text] of conditionOptions) {
    if (value === code) return text;
  }
  return code;
}

const emptyForm = {
  nickname: "",
  birthYear: "",
  gender: "",
  height: "",
  weight: "",
  smoking: "",
  drinking: "",
  caffeine: "",
  dietType: "",
  sunExposure: "",
};

const toText = (value) =>
  value === null || value === undefined ? "" : String(value);
const toNumber = (value) => (value.trim() === "" ? null : Number(value));
const toNullable = (value) => (value === "" ? null : value);

function toForm(user, profile) {
  return {
    nickname: user.nickname,
    birthYear: toText(profile.birthYear),
    gender: toText(profile.gender),
    height: toText(profile.height),
    weight: toText(profile.weight),
    smoking: toText(profile.smoking),
    drinking: toText(profile.drinking),
    caffeine: toText(profile.caffeine),
    dietType: toText(profile.dietType),
    sunExposure: toText(profile.sunExposure),
  };
}

export default function ProfileEditPage({userId, onBack, onSaved, onNotifications }) {
  const [user, setUser] = useState(null);
  const [form, setForm] = useState(emptyForm);
  // 선택한 질환 이름 목록, 빈 배열이면 "없음"
  const [conditions, setConditions] = useState([]);
  const [addingCondition, setAddingCondition] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [saving, setSaving] = useState(false);
  // { type: "error" | "success", text }
  const [message, setMessage] = useState(null);

  useEffect(() => {
    Promise.all([
      fetchUser(userId),
      fetchHealthProfile(userId),
      fetchConditions(userId),
    ])
      .then(([loadedUser, profile, loadedConditions]) => {
        setUser(loadedUser);
        setForm(toForm(loadedUser, profile));
        setConditions(loadedConditions.conditions);
      })
      .catch((e) => setLoadError(e.message || "내 정보를 불러오지 못했어요."));
  }, []);

  const change = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setMessage(null);
  };

  // 설문에서 고른 질환을 지우거나, 같은 선택지 중에서 추가 (저장 버튼을 눌러야 반영)
  const removeCondition = (name) => {
    setConditions((prev) => prev.filter((item) => item !== name));
    setMessage(null);
  };

  const addCondition = (name) => {
    setConditions((prev) => (prev.includes(name) ? prev : [...prev, name]));
    setAddingCondition(false);
    setMessage(null);
  };

  // 아직 고르지 않은 질환만 추가 선택지로 보여줌
  const remainingConditions = conditionOptions.filter(
    ([value]) => !conditions.includes(value),
  );

  // 저장 흐름: 닉네임(바뀐 경우만) → 건강 정보 → 질환, API 3개를 순서대로 호출
  // 중간에 실패하면 앞에서 저장된 항목은 서버에 남으므로, 화면에도 반영하고 어디까지 저장됐는지 알려준다
  const save = async () => {
    if (!user) return;
    setSaving(true);
    setMessage(null);
    // 저장에 성공한 항목 이름, 지금 저장 중인 항목 이름
    const savedItems = [];
    let currentItem = "";
    let savedUser = user;
    try {
      if (form.nickname.trim() !== user.nickname) {
        currentItem = "닉네임";
        savedUser = await updateNickname(userId, form.nickname);
        setUser(savedUser);
        savedItems.push("닉네임");
      }
      currentItem = "건강 정보";
      await saveHealthProfile(userId, {
        birthYear: toNumber(form.birthYear),
        gender: toNullable(form.gender),
        height: toNumber(form.height),
        weight: toNumber(form.weight),
        smoking: toNullable(form.smoking),
        drinking: toNullable(form.drinking),
        caffeine: toNullable(form.caffeine),
        dietType: toNullable(form.dietType),
        sunExposure: toNullable(form.sunExposure),
      });
      savedItems.push("건강 정보");
      currentItem = "질환";
      await saveConditions(userId, conditions);
      onSaved(savedUser);
      setMessage({ type: "success", text: "저장되었어요." });
    } catch (e) {
      // 실패 전에 닉네임이 저장됐으면 마이페이지에도 새 닉네임이 보이게 반영
      if (savedItems.includes("닉네임")) {
        onSaved(savedUser);
      }
      const reason = e.message || "알 수 없는 오류";
      let text = `${currentItem} 저장에 실패했어요. (${reason})`;
      if (savedItems.length > 0) {
        text = `${savedItems.join(", ")}은(는) 저장됐어요. ${text}`;
      }
      setMessage({ type: "error", text: text });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="screen profile-screen">
      <TopBar
        back={onBack}
        title="프로필 편집"
        description="추천과 영양 계산에 쓰이는 정보예요. 언제든 바꿀 수 있어요."
        onNotifications={onNotifications}
      />

      {loadError && <p className="profile-edit-message error">{loadError}</p>}
      {!loadError && !user && (
        <p className="profile-edit-message">불러오는 중...</p>
      )}

      {user && (
        <form
          className="profile-edit"
          onSubmit={(event) => {
            event.preventDefault();
            save();
          }}
        >
          <section className="profile-edit-card">
            <h2>기본 정보</h2>
            <div className="profile-edit-row">
              <span className="profile-edit-label">
                닉네임
                <small>친구가 나를 찾을 때 쓰는 이름이에요</small>
              </span>
              <div>
                <input
                  className="profile-edit-input"
                  aria-label="닉네임"
                  value={form.nickname}
                  maxLength={50}
                  onChange={(event) => change("nickname", event.target.value)}
                />
                <p className="profile-edit-hint">이메일 {user.email}</p>
              </div>
            </div>
          </section>

          <section className="profile-edit-card">
            <h2>건강 정보</h2>
            <div className="profile-edit-row">
              <span className="profile-edit-label">기본 건강 정보</span>
              <div className="profile-edit-grid">
                <label className="profile-edit-field">
                  <span>출생연도</span>
                  <input
                    type="number"
                    min={1900}
                    max={2100}
                    value={form.birthYear}
                    onChange={(event) => change("birthYear", event.target.value)}
                  />
                </label>
                <label className="profile-edit-field">
                  <span>키 (cm)</span>
                  <input
                    type="number"
                    step="0.1"
                    min={0}
                    value={form.height}
                    onChange={(event) => change("height", event.target.value)}
                  />
                </label>
                <label className="profile-edit-field">
                  <span>몸무게 (kg)</span>
                  <input
                    type="number"
                    step="0.1"
                    min={0}
                    value={form.weight}
                    onChange={(event) => change("weight", event.target.value)}
                  />
                </label>
              </div>
            </div>

            <div className="profile-edit-row">
              <span className="profile-edit-label">성별</span>
              {/* 설문 Q-GENDER 와 같은 값 (M / F) */}
              <ChoiceChips
                label="성별"
                value={form.gender}
                options={[
                  ["M", "남성"],
                  ["F", "여성"],
                ]}
                onChange={(value) => change("gender", value)}
              />
            </div>

            {choiceFields.map((field) => (
              <div className="profile-edit-row" key={field.key}>
                <span className="profile-edit-label">{field.label}</span>
                <ChoiceChips
                  label={field.label}
                  value={form[field.key]}
                  options={field.options}
                  onChange={(value) => change(field.key, value)}
                />
              </div>
            ))}
          </section>

          <section className="profile-edit-card">
            <h2>질환</h2>
            <div className="profile-edit-row">
            <span className="profile-edit-label">
              질환
              <small>여러 개 등록할 수 있어요</small>
            </span>
            <div>
            {conditions.length === 0 ? (
              <p className="profile-edit-hint">등록된 질환이 없어요.</p>
            ) : (
              <ul className="profile-edit-conditions">
                {conditions.map((name) => (
                  <li key={name}>
                    {conditionLabel(name)}
                    <button
                      type="button"
                      aria-label={`${conditionLabel(name)} 삭제`}
                      onClick={() => removeCondition(name)}
                    >
                      ✕
                    </button>
                  </li>
                ))}
              </ul>
            )}

            {addingCondition ? (
              <select
                className="profile-edit-condition-picker"
                defaultValue=""
                onChange={(event) => {
                  if (event.target.value) addCondition(event.target.value);
                }}
              >
                <option value="" disabled>
                  추가할 질환을 골라 주세요
                </option>
                {remainingConditions.map(([value, text]) => (
                  <option key={value} value={value}>
                    {text}
                  </option>
                ))}
              </select>
            ) : (
              <button
                type="button"
                className="profile-edit-condition-add"
                disabled={remainingConditions.length === 0}
                onClick={() => setAddingCondition(true)}
              >
                + 질환 추가
              </button>
            )}
            </div>
            </div>
          </section>

          {message && (
            <p className={`profile-edit-message ${message.type}`}>
              {message.text}
            </p>
          )}

          <div className="profile-edit-footer">
            <button className="btn btn-outline btn-lg" type="button" onClick={onBack}>
              취소
            </button>
            <button className="btn btn-primary btn-lg" type="submit" disabled={saving}>
              {saving ? "저장 중..." : "저장"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

// 선택지 칩 한 줄 (선택 안 함 포함). 예전에 다른 값으로 저장된 경우에도 칩으로 보이게 한다
function ChoiceChips({ label, value, options, onChange }) {
  const known = value === "" || options.some(([optionValue]) => optionValue === value);
  const all = [["", "선택 안 함"], ...options, ...(known ? [] : [[value, value]])];
  return (
    <div className="profile-edit-chips" role="radiogroup" aria-label={label}>
      {all.map(([optionValue, text]) => (
        <button
          key={optionValue || "none"}
          type="button"
          role="radio"
          aria-checked={value === optionValue}
          className={value === optionValue ? "choice active" : "choice"}
          onClick={() => onChange(optionValue)}
        >
          {text}
        </button>
      ))}
    </div>
  );
}
