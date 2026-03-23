"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuth";
import { gql, useQuery, useMutation } from "@apollo/client";
import Link from "next/link";

const USER_SETTINGS_QUERY = gql`
  query GetUserSettings {
    me {
      id
      email
      name
      apiKeys {
        id
        name
        createdAt
        lastUsedAt
      }
      preferences {
        theme
        emailNotifications
        defaultLLMProvider
        defaultResearchDepth
      }
    }
  }
`;

const UPDATE_PREFERENCES_MUTATION = gql`
  mutation UpdatePreferences($input: UpdatePreferencesInput!) {
    updatePreferences(input: $input) {
      id
      preferences {
        theme
        emailNotifications
        defaultLLMProvider
        defaultResearchDepth
      }
    }
  }
`;

const CREATE_API_KEY_MUTATION = gql`
  mutation CreateAPIKey($name: String!) {
    createAPIKey(name: $name) {
      id
      name
      key
      createdAt
    }
  }
`;

const DELETE_API_KEY_MUTATION = gql`
  mutation DeleteAPIKey($id: String!) {
    deleteAPIKey(id: $id) {
      success
    }
  }
`;

export default function SettingsPage() {
  const router = useRouter();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  const logout = useAuthStore((state) => state.logout);

  const [newKeyName, setNewKeyName] = useState("");
  const [showNewKey, setShowNewKey] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState(false);

  const { data, loading, refetch } = useQuery(USER_SETTINGS_QUERY);
  const [updatePreferences] = useMutation(UPDATE_PREFERENCES_MUTATION);
  const [createAPIKey] = useMutation(CREATE_API_KEY_MUTATION);
  const [deleteAPIKey] = useMutation(DELETE_API_KEY_MUTATION);

  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login?redirect=/settings");
    }
  }, [isAuthenticated, router]);

  const handleUpdatePreferences = async (field: string, value: any) => {
    try {
      const preferences = data?.me?.preferences || {};
      await updatePreferences({
        variables: {
          input: {
            ...preferences,
            [field]: value,
          },
        },
      });
      await refetch();
    } catch (error) {
      console.error("Failed to update preferences:", error);
      alert("Failed to update preferences");
    }
  };

  const handleCreateAPIKey = async () => {
    if (!newKeyName.trim()) {
      alert("Please enter a key name");
      return;
    }

    try {
      const result = await createAPIKey({
        variables: { name: newKeyName },
      });
      setShowNewKey(result.data.createAPIKey.key);
      setNewKeyName("");
      await refetch();
    } catch (error) {
      console.error("Failed to create API key:", error);
      alert("Failed to create API key");
    }
  };

  const handleDeleteAPIKey = async (id: string) => {
    if (!window.confirm("Are you sure you want to delete this API key?")) {
      return;
    }

    try {
      await deleteAPIKey({ variables: { id } });
      await refetch();
    } catch (error) {
      console.error("Failed to delete API key:", error);
      alert("Failed to delete API key");
    }
  };

  const handleLogout = () => {
    logout();
    router.push("/login");
  };

  const handleCopyKey = () => {
    if (showNewKey) {
      navigator.clipboard.writeText(showNewKey);
      setCopiedKey(true);
      setTimeout(() => setCopiedKey(false), 2000);
    }
  };

  if (!isAuthenticated) {
    return null;
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center">
        <div className="text-center">
          <div className="inline-block w-12 h-12 border-4 border-slate-600 border-t-blue-500 rounded-full animate-spin mb-4" />
          <p className="text-white text-lg">Loading settings...</p>
        </div>
      </div>
    );
  }

  const user = data?.me;
  const preferences = user?.preferences || {};
  const apiKeys = user?.apiKeys || [];

  return (
    <div className="min-h-screen bg-slate-900">
      {/* Header */}
      <header className="bg-slate-800 border-b border-slate-700 sticky top-0 z-50">
        <div className="max-w-4xl mx-auto px-6 py-4">
          <Link
            href="/dashboard"
            className="text-blue-400 hover:text-blue-300 text-sm mb-2 block"
          >
            ← Back to Dashboard
          </Link>
          <h1 className="text-2xl font-bold text-white">Settings</h1>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-4xl mx-auto px-6 py-8">
        {/* Account Section */}
        <section className="mb-8">
          <h2 className="text-xl font-semibold text-white mb-4">Account</h2>
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-6 space-y-4">
            <div>
              <label className="block text-sm text-slate-400 mb-2">Email</label>
              <p className="text-white font-medium">{user?.email}</p>
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-2">Name</label>
              <p className="text-white font-medium">{user?.name}</p>
            </div>
            <div className="pt-4 border-t border-slate-700">
              <button
                onClick={handleLogout}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-lg transition"
              >
                Logout
              </button>
            </div>
          </div>
        </section>

        {/* Preferences Section */}
        <section className="mb-8">
          <h2 className="text-xl font-semibold text-white mb-4">Preferences</h2>
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-6 space-y-6">
            {/* Theme */}
            <div>
              <label className="block text-sm font-medium text-white mb-2">
                Theme
              </label>
              <select
                value={preferences.theme || "dark"}
                onChange={(e) =>
                  handleUpdatePreferences("theme", e.target.value)
                }
                className="w-full bg-slate-700 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:border-blue-500"
              >
                <option value="dark">Dark</option>
                <option value="light">Light</option>
                <option value="auto">System</option>
              </select>
            </div>

            {/* Default LLM Provider */}
            <div>
              <label className="block text-sm font-medium text-white mb-2">
                Default LLM Provider
              </label>
              <select
                value={preferences.defaultLLMProvider || "openai"}
                onChange={(e) =>
                  handleUpdatePreferences("defaultLLMProvider", e.target.value)
                }
                className="w-full bg-slate-700 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:border-blue-500"
              >
                <option value="openai">OpenAI (GPT-4)</option>
                <option value="anthropic">Anthropic (Claude)</option>
                <option value="google">Google (Gemini)</option>
              </select>
            </div>

            {/* Default Research Depth */}
            <div>
              <label className="block text-sm font-medium text-white mb-2">
                Default Research Depth
              </label>
              <select
                value={preferences.defaultResearchDepth || "standard"}
                onChange={(e) =>
                  handleUpdatePreferences(
                    "defaultResearchDepth",
                    e.target.value,
                  )
                }
                className="w-full bg-slate-700 border border-slate-600 rounded px-3 py-2 text-white focus:outline-none focus:border-blue-500"
              >
                <option value="quick">Quick (5-10 minutes)</option>
                <option value="standard">Standard (15-20 minutes)</option>
                <option value="deep">Deep (30-45 minutes)</option>
              </select>
            </div>

            {/* Email Notifications */}
            <div>
              <label className="block text-sm font-medium text-white mb-2">
                Email Notifications
              </label>
              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={preferences.emailNotifications ?? true}
                  onChange={(e) =>
                    handleUpdatePreferences(
                      "emailNotifications",
                      e.target.checked,
                    )
                  }
                  className="w-4 h-4 rounded bg-slate-700 border border-slate-600 accent-blue-500"
                />
                <span className="text-slate-300">
                  Send email when research is complete
                </span>
              </label>
            </div>
          </div>
        </section>

        {/* API Keys Section */}
        <section className="mb-8">
          <h2 className="text-xl font-semibold text-white mb-4">API Keys</h2>

          {/* New Key Display */}
          {showNewKey && (
            <div className="bg-green-900/20 border border-green-700 rounded-lg p-4 mb-6">
              <p className="text-green-400 text-sm font-medium mb-2">
                API Key Created Successfully
              </p>
              <p className="text-slate-400 text-sm mb-3">
                Store this key securely. You won't be able to see it again.
              </p>
              <div className="flex gap-2">
                <code className="flex-1 bg-slate-900 border border-slate-700 rounded px-3 py-2 text-white font-mono text-sm overflow-x-auto">
                  {showNewKey}
                </code>
                <button
                  onClick={handleCopyKey}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition"
                >
                  {copiedKey ? "✓ Copied" : "Copy"}
                </button>
              </div>
            </div>
          )}

          {/* Create New Key */}
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-6 mb-6">
            <h3 className="text-lg font-semibold text-white mb-4">
              Create New API Key
            </h3>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Key name (e.g., 'CLI Tool', 'Mobile App')"
                value={newKeyName}
                onChange={(e) => setNewKeyName(e.target.value)}
                className="flex-1 bg-slate-700 border border-slate-600 rounded px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
              />
              <button
                onClick={handleCreateAPIKey}
                className="px-6 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition"
              >
                Create
              </button>
            </div>
          </div>

          {/* Existing Keys */}
          <div className="bg-slate-800 rounded-lg border border-slate-700 p-6">
            <h3 className="text-lg font-semibold text-white mb-4">
              Existing Keys
            </h3>
            {apiKeys.length === 0 ? (
              <p className="text-slate-400 text-sm">No API keys created yet.</p>
            ) : (
              <div className="space-y-3">
                {apiKeys.map((key: any) => (
                  <div
                    key={key.id}
                    className="flex items-center justify-between p-4 bg-slate-700/50 rounded border border-slate-600"
                  >
                    <div>
                      <p className="text-white font-medium">{key.name}</p>
                      <p className="text-slate-400 text-xs">
                        Created {new Date(key.createdAt).toLocaleDateString()}
                        {key.lastUsedAt &&
                          ` • Last used ${new Date(key.lastUsedAt).toLocaleDateString()}`}
                      </p>
                    </div>
                    <button
                      onClick={() => handleDeleteAPIKey(key.id)}
                      className="px-3 py-1 text-red-400 hover:text-red-300 text-sm font-medium"
                    >
                      Delete
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>

        {/* Danger Zone */}
        <section className="mb-8">
          <h2 className="text-xl font-semibold text-white mb-4">Danger Zone</h2>
          <div className="bg-red-900/10 rounded-lg border border-red-700/50 p-6">
            <p className="text-slate-300 text-sm mb-4">
              These actions cannot be undone. Please proceed with caution.
            </p>
            <button className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-lg transition">
              Delete Account
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}
