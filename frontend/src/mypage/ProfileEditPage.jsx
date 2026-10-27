import { useEffect, useState } from "react";
import { TopBar } from "../shared/ui";
import {
  fetchConditions,
  fetchHealthProfile,
  fetchUser,
  MY_USER_ID,
  saveConditions,
  saveHealthProfile,
  updateNickname,
} from "./mypageApi";

// 선택지 값은 schema.sql 예시 데이터 기준
const choiceFields = [
  {
    key: "smoking",
    label: "흡연",
    options: [["NONE", "안 함"], ["FORMER", "과거 흡연"], ["CURRENT", "흡연 중"]],
  },
  {
    key: "drinking",
    label: "음주",
    options: [["NONE", "안 함"], ["OCCASIONAL", "가끔"], ["FREQUENT", "자주"]],
  },
  {
    key: "caffeine",
    label: "카페인",
    options: [["LOW", "적음"], ["MEDIUM", "보통"], ["HIGH", "많음"]],
  },
  {
    key: "dietType",
    label: "식단",
    options: [["BALANCED", "균형 잡힌 식사"], ["EATING_OUT", "외식 위주"], ["LOW_VEGGIE", "채소 적게"]],
  },
  {
    key: "sunExposure",
    label: "햇빛 노출",
    options: [["LOW", "적음"], ["MEDIUM", "보통"], ["HIGH", "많음"]],
  },
];

// 질환 추가 선택지 (schema.sql 예시 데이터 기준)
const conditionOptions = ["고혈압", "고지혈증", "당뇨"];

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

const toText = (value) => (value === null || value === undefined ? "" : String(value));
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

export default function ProfileEditPage({ onBack, onSaved, onNotifications }) {
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
    Promise.all([fetchUser(MY_USER_ID), fetchHealthProfile(MY_USER_ID), fetchConditions(MY_USER_ID)])
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

  const remainingConditions = conditionOptions.filter((name) => !conditions.includes(name));

  const save = async () => {
    if (!user) return;
    setSaving(true);
    setMessage(null);
    try {
      let savedUser = user;
      if (form.nickname.trim() !== user.nickname) {
        savedUser = await updateNickname(MY_USER_ID, form.nickname);
        setUser(savedUser);
      }
      await saveHealthProfile(MY_USER_ID, {
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
      await saveConditions(MY_USER_ID, conditions);
      onSaved(savedUser);
      setMessage({ type: "success", text: "저장되었어요." });
    } catch (e) {
      setMessage({ type: "error", text: e.message || "저장하지 못했어요." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="screen profile-screen">
      <TopBar back={onBack} title="내 정보" subtitle="프로필 편집" onNotifications={onNotifications} />

      {loadError && <p className="profile-edit-message error">{loadError}</p>}
      {!loadError && !user && <p className="profile-edit-message">불러오는 중...</p>}

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
            <label className="profile-edit-field">
              <span>닉네임</span>
              <input
                value={form.nickname}
                maxLength={50}
                onChange={(event) => change("nickname", event.target.value)}
              />
            </label>
            <p className="profile-edit-hint">이메일 {user.email}</p>
          </section>

          <section className="profile-edit-card">
            <h2>건강 정보</h2>
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
                <span>성별</span>
                <select value={form.gender} onChange={(event) => change("gender", event.target.value)}>
                  <option value="">선택 안 함</option>
                  <option value="FEMALE">여성</option>
                  <option value="MALE">남성</option>
                  {/* 예전에 다른 값으로 저장된 경우에도 그대로 보이게 */}
                  {form.gender !== "" && form.gender !== "FEMALE" && form.gender !== "MALE" && (
                    <option value={form.gender}>{form.gender}</option>
                  )}
                </select>
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
              {choiceFields.map((field) => (
                <label className="profile-edit-field" key={field.key}>
                  <span>{field.label}</span>
                  <select value={form[field.key]} onChange={(event) => change(field.key, event.target.value)}>
                    <option value="">선택 안 함</option>
                    {field.options.map(([value, text]) => (
                      <option key={value} value={value}>{text}</option>
                    ))}
                    {/* 예전에 다른 값으로 저장된 경우에도 그대로 보이게 */}
                    {form[field.key] !== "" && !field.options.some(([value]) => value === form[field.key]) && (
                      <option value={form[field.key]}>{form[field.key]}</option>
                    )}
                  </select>
                </label>
              ))}
            </div>
          </section>

          <section className="profile-edit-card">
            <h2>질환</h2>
            {conditions.length === 0 ? (
              <p className="profile-edit-hint">등록된 질환이 없어요.</p>
            ) : (
              <ul className="profile-edit-conditions">
                {conditions.map((name) => (
                  <li key={name}>
                    {name}
                    <button type="button" aria-label={`${name} 삭제`} onClick={() => removeCondition(name)}>
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
                {remainingConditions.map((name) => (
                  <option key={name} value={name}>{name}</option>
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
          </section>

          {message && <p className={`profile-edit-message ${message.type}`}>{message.text}</p>}

          <button className="profile-edit-save" type="submit" disabled={saving}>
            {saving ? "저장 중..." : "저장"}
          </button>
        </form>
      )}
    </div>
  );
}
