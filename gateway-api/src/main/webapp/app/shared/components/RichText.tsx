import DOMPurify from 'dompurify';

const CONFIG = {
  ALLOWED_TAGS: ['p', 'br', 'b', 'strong', 'i', 'em', 'u', 'ul', 'ol', 'li', 'img', 'a', 'h1', 'h2', 'h3', 'h4', 'blockquote', 'span', 'div'],
  ALLOWED_ATTR: ['src', 'alt', 'href', 'title', 'width', 'height'],
  ALLOWED_URI_REGEXP: /^(?:https?:|\/)/i,
};

/** Announce descriptions may be HTML (the migrated litemall goods carry <p> and <img>): render them sanitised. */
export function looksLikeHtml(text: string | null | undefined): boolean {
  return !!text && /<\s*(p|br|img|b|i|ul|ol|li|strong|em|div|span|h[1-6])\b/i.test(text);
}

export default function RichText({ html, className }: { html: string; className?: string }) {
  const clean = DOMPurify.sanitize(html, { ...CONFIG, ADD_ATTR: ['target', 'rel'] });
  return <div className={`an-richtext ${className ?? ''}`} dangerouslySetInnerHTML={{ __html: clean }} />;
}
