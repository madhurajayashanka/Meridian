import "./globals.css";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Meridian",
  description: "Autonomous multi-agent research platform",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
