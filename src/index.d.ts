import type { Plugin } from "unified";
import type { Root } from "mdast";

export interface ConvertOptions {
  stripHighlights?: boolean;
}

/** Remark plugin that parses markdown with longdown extensions (preserves leading whitespace). */
export declare const remarkParseLongdown: Plugin<[], string, Root>;

/** Remark plugin that transforms flat headings into a nested list structure. */
export declare const remarkStratify: Plugin<[], Root>;

/** Remark plugin that strips heading markers, leading numbers, and bold formatting. */
export declare const remarkStripHighlights: Plugin<[], Root>;

/** Convert longform markdown to outline format. */
export declare function longformToOutline(
  input: string,
  opts?: ConvertOptions
): string;

/** Convert HTML to outline format. */
export declare function htmlToOutline(
  input: string,
  opts?: ConvertOptions
): string;
