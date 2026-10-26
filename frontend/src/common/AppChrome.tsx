import { Icon, type IconName, type Tab } from "../shared/ui";

export function BottomNav({
                            tab,
                            onChange,
                          }: {
  tab: Tab;
  onChange: (tab: Tab) => void;
}) {
  const items: { id: Tab; label: string; icon: IconName }[] = [
    { id: "home", label: "홈", icon: "home" },
    { id: "friends", label: "친구 영양정보", icon: "people" },
    { id: "disposal", label: "폐의약품 지도", icon: "pin" },
    { id: "my", label: "마이페이지", icon: "user" },
  ];

  return (
      <nav className="bottom-nav" aria-label="주요 메뉴">
        {items.map((item) => (
            <button
                className={tab === item.id ? "active" : ""}
                key={item.id}
                onClick={() => onChange(item.id)}
            >
          <span className="nav-icon">
            <Icon name={item.icon} size={21} />
          </span>
              <span>{item.label}</span>
            </button>
        ))}
      </nav>
  );
}