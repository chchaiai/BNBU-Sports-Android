import type { AppConfig } from "./config/env";
import type { ContactChannel } from "./types";
import { AppError } from "./errors";

const CODE_TTL_SECONDS = 10 * 60;

export { CODE_TTL_SECONDS };

/**
 * Delegates delivery to the university notification service. Keeping this as
 * a small HTTP boundary lets operations choose their approved mail/SMS vendor
 * without putting provider credentials in the app or database.
 */
export async function deliverContactCode(
  config: AppConfig,
  channel: ContactChannel,
  recipient: string,
  code: string
): Promise<void> {
  if (!config.CONTACT_CODE_DELIVERY_WEBHOOK_URL) {
    if (config.NODE_ENV !== "production") {
      // Local-only convenience for emulator and integration testing. Never log
      // verification secrets from a production environment.
      console.info(`[contact-code:${channel}] ${recipient}: ${code}`);
      return;
    }
    throw new AppError(503, "CONTACT_DELIVERY_UNAVAILABLE", "验证码服务暂不可用，请稍后重试");
  }
  let response: Response;
  try {
    response = await fetch(config.CONTACT_CODE_DELIVERY_WEBHOOK_URL, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ channel, to: recipient, code, expiresInSeconds: CODE_TTL_SECONDS })
    });
  } catch {
    throw new AppError(503, "CONTACT_DELIVERY_UNAVAILABLE", "验证码服务暂不可用，请稍后重试");
  }
  if (!response.ok) {
    throw new AppError(503, "CONTACT_DELIVERY_UNAVAILABLE", "验证码服务暂不可用，请稍后重试");
  }
}
