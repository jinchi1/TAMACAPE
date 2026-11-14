// Stores counters only: no family text, user identifiers, or credentials.
export function reserveBudget(previous, now) {
  const day = new Date(now).toISOString().slice(0,10);
  const recent = (previous?.recent || []).filter(time => time > now - 60000);
  const count = previous?.day === day ? previous.count : 0;
  if (count >= 100) return {allowed:false,reason:"daily_limit"};
  if (recent.length >= 6) return {allowed:false,reason:"rate_limited"};
  return {allowed:true,state:{day,count:count+1,recent:[...recent,now]}};
}
export class FamilyBudget {
  constructor(state) { this.state = state; }
  async fetch(request) {
    if (new URL(request.url).pathname === "/song") {
      const key = await request.text();
      return this.state.blockConcurrencyWhile(async()=>{
        const day = new Date().toISOString().slice(0,10);
        const previous = await this.state.storage.get("song-budget");
        const keys = previous?.day === day ? previous.keys : [];
        if (keys.length >= 10 || keys.includes(key)) return new Response(null,{status:429});
        await this.state.storage.put("song-budget",{day,keys:[...keys,key]});
        return new Response(null,{status:204});
      });
    }
    return this.state.blockConcurrencyWhile(async()=>{
      const decision=reserveBudget(await this.state.storage.get("budget"),Date.now());
      if(decision.allowed) await this.state.storage.put("budget",decision.state);
      return Response.json({allowed:decision.allowed,reason:decision.reason || null});
    });
  }
}
