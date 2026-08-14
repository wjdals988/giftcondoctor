const nextConfig = {
  reactStrictMode: true,
  // firebase-admin 14 depends on CommonJS jwks-rsa, which imports ESM-only jose.
  // Bundle this boundary so Vercel's Node runtime does not call require("jose").
  transpilePackages: ["firebase-admin", "jwks-rsa", "jose"],
};

export default nextConfig;
