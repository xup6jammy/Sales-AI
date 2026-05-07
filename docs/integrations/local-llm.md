# Local LLM Integration (OpenAI-Compatible Endpoint)

## Why Local LLM?

Every cloud LLM integration (Anthropic, OpenAI, Gemini) sends the full message
body to a third-party server. For most SaaS deployments that is acceptable. For
a growing set of operators it is not:

- **Banking and insurance** — Customer communication is often subject to
  data-residency rules (e.g. MAS TRM, DORA, local banking acts) that prohibit
  sending data to overseas infrastructure.
- **Manufacturing and ERP** — Deal terms, pricing, and supply chain details in
  email threads can be commercially sensitive trade secrets.
- **GDPR strictness** — The "adequacy decision" landscape for US-based cloud
  providers remains legally contested. Some DPOs take the position that
  personal data in email bodies must not leave the EEA.
- **Air-gapped environments** — Factory floors, secure government facilities,
  and financial trading systems may have no internet egress at all.

Sales-AI is built for this. The manifesto promise:

> 對銀行 / 保險 / 製造 / ERP 等監管產業:Java 21、零依賴、可直接內嵌進你的後端
> ——不需要把客戶資料送出去給第三方雲。

With `--llm openai-compatible --llm-endpoint`, every token stays on hardware
you control.

---

## The Promise

When you point Sales-AI at a local LLM server:

- **Zero data egress** — the HTTP request goes to `localhost` (or your internal
  network). Customer names, deal values, and email bodies never leave your
  perimeter.
- **No API key dependency** — no key rotation, no key-leak risk, no vendor
  outage dependency.
- **Same audit trail** — the same `LlmCallAuditEntry` fields are recorded;
  `cost=$0.00` and `provider=openai-compatible`.
- **Same CLI interface** — one flag swap from a cloud provider to fully on-prem.

---

## Three Popular OpenAI-Compatible Local Servers

### Ollama — Easiest Setup

Best for: laptops, developer workstations, small-team deployments, quick
evaluation.

```sh
# Install: https://ollama.ai (one-line installer for macOS/Linux/Windows)
ollama pull llama3.1:70b
ollama serve
# Server now listening at http://localhost:11434/v1
```

Ollama automatically manages model files, quantization, and GPU/CPU offloading.
Models are stored in `~/.ollama/models`. The OpenAI-compatible API path is
`/v1/chat/completions` — identical to what Sales-AI sends.

### vLLM — Production-Grade, GPU-Optimized

Best for: multi-user production deployments, high-throughput batch processing,
teams with dedicated GPU servers.

```sh
pip install vllm
vllm serve meta-llama/Llama-3.1-70B-Instruct \
  --host 0.0.0.0 \
  --port 8000 \
  --tensor-parallel-size 2   # for 2-GPU setups
# Server now listening at http://your-server:8000/v1
```

vLLM supports continuous batching and PagedAttention, which dramatically
increases throughput compared to naive inference. Requires Linux + NVIDIA GPU
with CUDA 12.1+. Hugging Face token needed for gated models (Llama).

### llama.cpp — Minimal / CPU-Friendly

Best for: CPU-only servers, extremely constrained environments, edge/IoT,
maximum portability.

```sh
git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
cmake -B build -DLLAMA_OPENAI_COMPAT=ON
cmake --build build --config Release -j$(nproc)

# Download a GGUF model (example: Q4_K_M quantized Llama 3.1 8B)
# Then run the server:
./build/bin/llama-server \
  --model models/llama-3.1-8b-q4_k_m.gguf \
  --port 8080 \
  --host 0.0.0.0
# Server now listening at http://your-server:8080/v1
```

The `-DLLAMA_OPENAI_COMPAT=ON` flag enables the `/v1/chat/completions` endpoint
that Sales-AI expects. llama.cpp can also use Apple Silicon (Metal), CUDA, or
Vulkan backends with the appropriate CMake flags.

---

## Wiring It Up

```sh
# No API key needed for most local servers
java -cp out com.example.salesai.SalesAiCli \
  --llm openai-compatible \
  --llm-endpoint http://localhost:11434/v1 \
  --llm-model llama3.1:70b \
  --email vip@enterprise.com
```

For a vLLM server on a different host:

```sh
java -cp out com.example.salesai.SalesAiCli \
  --llm openai-compatible \
  --llm-endpoint http://gpu-server-01.internal:8000/v1 \
  --llm-model meta-llama/Llama-3.1-70B-Instruct \
  --email vip@enterprise.com
```

If your local server requires an API key (some vLLM deployments add auth
via a reverse proxy), set `OPENAI_API_KEY` to the value your server expects;
Sales-AI will include it in the `Authorization: Bearer` header automatically.

---

## Hardware Requirements (Honest)

Model size determines VRAM requirements. These are the minimum VRAM numbers for
full-precision (BF16) inference; quantized models reduce requirements roughly
proportionally to quantization level.

| Model size | Precision   | VRAM needed         | Example hardware                    |
|------------|-------------|---------------------|-------------------------------------|
| 70B        | BF16/FP16   | ~140 GB             | 4x A100 80GB or 8x RTX 4090         |
| 70B        | Q4_K_M      | ~40 GB              | 1x A100 80GB or 2x RTX 4090 (NVLink)|
| 8B         | BF16/FP16   | ~16 GB              | 1x RTX 4080 or any A10G             |
| 8B         | Q4_K_M      | ~6 GB               | Any modern consumer GPU (RTX 3060+) |
| CPU-only   | Q4_K_M 8B   | No GPU required     | ~30s per draft on a fast 16-core CPU|

**Practical guidance:**

- For production B2B email quality, use at least a **70B model** if budget allows.
  The quality gap between 8B and 70B is noticeable for nuanced sales language.
- For a cost-effective on-prem start, a single **RTX 4090 (24GB)** can run
  a Q4-quantized 13B model at acceptable latency (~5-8s per call).
- CPU-only is viable for low-volume deployments or evaluation, but latency
  (~30s on a fast server CPU) will be frustrating at scale.

---

## Honest Performance Comparison

| Aspect              | Cloud (Claude 3.5 Sonnet / GPT-4o) | Local 70B (Ollama, Q4)  |
|---------------------|-------------------------------------|--------------------------|
| Latency             | 1–3 s                               | 5–30 s                   |
| Draft quality       | Higher (especially nuanced B2B tone)| Strong but variable      |
| JSON schema adherence | Very high (native support)        | Good with prompt guidance|
| Cost per call       | $0.002–0.008                        | $0.00 (after hardware)   |
| Data egress         | Yes — full message bodies sent      | No — stays on your LAN   |
| Setup time          | Minutes (get key, set env var)      | Hours (server + model)   |
| Availability        | SLA-backed (99.9%+)                 | Depends on your infra    |
| Max context window  | 200K (Claude) / 128K (GPT-4o)       | 128K (Llama 3.1 70B)     |

The quality gap is real but narrowing. For straightforward follow-up emails and
routine replies, a local 70B model is difficult to distinguish from a cloud
model. For highly nuanced negotiation drafts or multilingual contexts, cloud
models still have an edge.

---

## Audit Log Entry

The audit log entry for local LLM calls is identical in structure to cloud
calls, with two differences:

| Field              | Value for local LLM                        |
|--------------------|--------------------------------------------|
| `provider`         | `openai-compatible`                        |
| `estimatedCostUsd` | `0.00`                                     |

All other fields (model, input/output tokens, latency, promptSha256,
workflowStep) are recorded normally. This means your SOC 2 / ISO 27001 audit
trail is complete even for fully on-prem deployments — you can still
reconstruct which model and which prompt produced every output, without the
cost field being misleading.

---

## Recommended Local Models for B2B Email Drafting

Not all open models are equal for sales email quality. Based on internal
testing against the Sales-AI system prompt:

| Model                      | Notes                                                  |
|----------------------------|--------------------------------------------------------|
| `llama3.1:70b`             | Best overall quality. First choice for production.    |
| `qwen2.5:72b`              | Strong multilingual (including Chinese). Competitive. |
| `mistral-nemo:12b`         | Good cost/performance for smaller GPU budgets.        |
| `llama3.1:8b`              | Fast, acceptable for low-stakes replies. Not for VIP. |
| Any model < 7B parameters  | **Avoid** — draft quality drops noticeably. JSON      |
|                            | adherence is unreliable below 7B.                     |

For Chinese-language customer communications, `qwen2.5:72b` often outperforms
Llama models. Pull it with `ollama pull qwen2.5:72b`.

---

## Troubleshooting

**Connection refused on llm-endpoint**
The local server is not running or is listening on a different port. Verify:
```sh
curl http://localhost:11434/v1/models   # Ollama
curl http://localhost:8000/v1/models    # vLLM
```

**Model not found (404)**
The `--llm-model` value does not match a loaded model name. For Ollama, run
`ollama list` to see loaded models. Model names are case-sensitive.

**Slow responses (>30s)**
You are likely running on CPU or the model is larger than available VRAM,
causing heavy GPU memory swapping. Options:
- Use a smaller quantized model (Q4_K_M 8B instead of 70B).
- Add more VRAM (another GPU or a GPU upgrade).
- Accept the latency if data residency is the top priority — 30s is slow but
  still produces a good draft.

**JSON parse error in LlmReplyDraftService**
Open models can be less reliable at returning strict JSON than cloud models.
Try:
1. Switching to a larger model (`llama3.1:70b` instead of `llama3.1:8b`).
2. Lowering the temperature in the server config (e.g. `--temp 0.1` in
   llama.cpp's server flags).
3. File an issue with the `promptSha256` from the audit log.
