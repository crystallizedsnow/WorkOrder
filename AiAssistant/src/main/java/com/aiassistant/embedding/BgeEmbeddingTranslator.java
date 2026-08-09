package com.aiassistant.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/**
 * BGE 系列嵌入模型的自定义 Translator。
 * <p>
 * DJL 自带的 {@code TextEmbeddingTranslatorFactory} 仅适用于 PyTorch 引擎，
 * 对 BGE-small-zh-v1.5 ONNX 模型存在输入名称不匹配问题（BGE 输入包含
 * input_ids/attention_mask/token_type_ids 三个，且输出为 last_hidden_state）。
 * <p>
 * 本 Translator 完成：
 * <ol>
 *   <li>processInput：使用 {@link HuggingFaceTokenizer} 分词，构造
 *       input_ids / attention_mask / token_type_ids 三个张量（含 batch 维度）</li>
 *   <li>processOutput：对 last_hidden_state 按 attention_mask 做均值池化，
 *       再 L2 归一化（BGE 推理必需，与 sentence-transformers 一致）</li>
 * </ol>
 * attention_mask 通过 {@link TranslatorContext#setAttachment} 在输入/输出之间传递。
 * <p>
 * {@link #getBatchifier()} 返回 null，由 Translator 自行管理 batch 维度，
 * 避免 STACK 在 unbatchify 时对单条样本做意外 squeeze。
 * <p>
 * 注意：ONNX Runtime NDArray 不支持 {@code norm()} / {@code normalize()} 等算子，
 * 故 L2 归一化在 Java 层用 float[] 完成。
 */
public class BgeEmbeddingTranslator implements Translator<String, float[]> {

    private final HuggingFaceTokenizer tokenizer;
    private final boolean normalize;

    public BgeEmbeddingTranslator(HuggingFaceTokenizer tokenizer, boolean normalize) {
        this.tokenizer = tokenizer;
        this.normalize = normalize;
    }

    @Override
    public Batchifier getBatchifier() {
        // 自管理 batch 维度：processInput 返回 [1, seq]，processOutput 接收 [1, seq, hidden]
        return null;
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        // 分词：返回 ids / attentionMask / typeIds
        Encoding encoding = tokenizer.encode(input);
        long[] ids = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] typeIds = encoding.getTypeIds();

        NDManager manager = ctx.getNDManager();
        // shape = [1, seq_len]（含 batch 维度，符合 ONNX 模型期望的 rank=2）
        NDArray idArray = manager.create(ids, new Shape(1, ids.length));
        idArray.setName("input_ids");
        NDArray attentionArray = manager.create(attentionMask, new Shape(1, attentionMask.length));
        attentionArray.setName("attention_mask");
        NDArray typeArray = manager.create(typeIds, new Shape(1, typeIds.length));
        typeArray.setName("token_type_ids");

        // 保存 attention_mask 供 processOutput 使用（mean pooling 需要它）
        ctx.setAttachment("attention_mask", attentionArray);

        return new NDList(idArray, attentionArray, typeArray);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        if (list == null || list.isEmpty()) {
            return new float[0];
        }
        // last_hidden_state: [1, seq, hidden]
        NDArray hidden = list.get(0);
        NDArray attention = (NDArray) ctx.getAttachment("attention_mask");
        if (attention == null) {
            // 兜底：取 [CLS]（第一个 token）作为句向量
            NDArray clsVec = hidden.get(0, 0);
            return postProcess(clsVec);
        }

        // mean pooling：sum(hidden * mask) / sum(mask)
        // hidden: [1, seq, hidden]；mask: [1, seq] -> reshape 为 [1, seq, 1] 广播
        NDArray maskExpanded = attention.reshape(1, -1, 1);
        NDArray maskedHidden = hidden.mul(maskExpanded);
        // 在 seq 维度（axis=1）求和（DJL 接受 int[] 维度）
        NDArray sumHidden = maskedHidden.sum(new int[]{1});
        // attention_mask 在 seq 维度求和：[1, 1]
        NDArray maskSum = attention.sum(new int[]{1});
        // 防止除零
        NDArray pooled = sumHidden.div(maskSum.add(1e-9f));

        return postProcess(pooled);
    }

    private float[] postProcess(NDArray pooled) {
        // 拍平为 [hidden] 并取出 float[]（ONNX Runtime NDArray 部分算子未实现，
        // 直接在 Java 层完成 L2 归一化更可靠）
        NDArray vec = pooled.reshape(-1);
        float[] arr = vec.toFloatArray();
        if (normalize) {
            // L2 归一化：sqrt(sum(x^2))，然后逐元素除
            float sumSq = 0f;
            for (float v : arr) sumSq += v * v;
            float norm = (float) Math.sqrt(sumSq);
            if (norm > 0) {
                for (int i = 0; i < arr.length; i++) arr[i] /= norm;
            }
        }
        return arr;
    }
}
