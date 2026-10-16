// Deploy: supabase functions deploy hello
// (verify_jwt is on by default; the app sends the user's or the publishable key as the bearer token)
Deno.serve(async (req) => {
  const { name = "world" } = await req.json().catch(() => ({}));
  return new Response(
    JSON.stringify({ message: `Hello ${name}!`, at: new Date().toISOString() }),
    { headers: { "Content-Type": "application/json" } },
  );
});
