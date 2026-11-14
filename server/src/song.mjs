export const styles = {
  bright: ["bright", "playful", "piano", "xylophone", "light percussion"],
  gentle: ["gentle", "warm", "soft piano", "bells", "light percussion"],
};
export const ages = ["4-6", "7-9"];

export function parseLyrics(text) {
  const value = JSON.parse(text);
  const lines = [...value.verse, ...value.chorus];
  if (typeof value.title !== "string" || !value.title.trim() || value.title.length > 60 ||
      !Array.isArray(value.verse) || !Array.isArray(value.chorus) ||
      ![value.verse, value.chorus].every(section => section.length >= 2 && section.length <= 4) ||
      !lines.every(line => typeof line === "string" && line.trim() && line.length <= 60 &&
        !/[\r\n\[\]]/.test(line)) || /https?:\/\//i.test(text)) throw Error("invalid_lyrics");
  return {title:value.title.trim(), verse:value.verse, chorus:value.chorus};
}

export function musicPayload(lyrics, style) {
  return {
    model_id:"music_v2_5",
    composition_plan:{chunks:[["Verse",lyrics.verse],["Chorus",lyrics.chorus]].map(([name,lines])=>({
      text:"["+name+"]\n"+lines.join("\n"),
      duration_ms:15000,
      positive_styles:["Korean vocals", "original children's nursery song", ...styles[style]],
      negative_styles:["spoken narration", "aggressive vocals"],
      context_adherence:"high",
    }))},
  };
}
