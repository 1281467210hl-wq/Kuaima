import { ref } from "vue";

import { getFaqs } from "@/api/chat";

const defaultServiceFaqs = [
  {
    question: "如何报名岗位？",
    answer: "进入岗位详情，点击报名并按提示完成后续流程即可。",
  },
  {
    question: "报名后如何确认录用？",
    answer: "老板确认录用后，订单状态会更新为已录用，你可在这里查看进度。",
  },
  {
    question: "工资什么时候到账？",
    answer: "工作完成并结算成功后，工资会进入你的钱包，可按平台规则提现。",
  },
  {
    question: "遇到问题如何联系客服？",
    answer: "点击下方人工客服，或选择常见问题后点击咨询此问题。",
  },
];

export function useServiceFaqs(fallbackFaqs = defaultServiceFaqs) {
  const hotQuestions = ref(fallbackFaqs);

  async function loadServiceFaqs() {
    try {
      const result = await getFaqs();
      const rows = Array.isArray(result)
        ? result
        : result?.records || result?.content || [];
      const configuredFaqs = rows
        .map((item, index) => ({
          id: item.id || `faq-${index}`,
          question: String(item.question || "").trim(),
          answer: String(item.answer || "").trim(),
        }))
        .filter((item) => item.question);
      hotQuestions.value = configuredFaqs.length ? configuredFaqs : fallbackFaqs;
    } catch (_) {
      hotQuestions.value = fallbackFaqs;
    }
  }

  return { hotQuestions, loadServiceFaqs };
}
