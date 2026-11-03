import { useEffect, useState } from "react";
import InteractionWarningList from "./InteractionWarningList.jsx";

// 저장된 처방 하나의 상충 정보 (달력 날짜 모달용)
// fetchInteractions(prescriptionId) => Promise<InteractionCheckResponse>  (api 파일에서 unwrap 된 값)
export default function InteractionNotice({
  prescriptionId,
  fetchInteractions,
}) {
  const [data, setData] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let ignore = false;
    setData(null);
    setFailed(false);
    fetchInteractions(prescriptionId)
      .then((res) => !ignore && setData(res))
      .catch(() => !ignore && setFailed(true));
    return () => {
      ignore = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prescriptionId]);

  if (failed)
    return <p className="interaction-empty">상충 정보를 불러오지 못했어요.</p>;
  return <InteractionWarningList data={data} />;
}
