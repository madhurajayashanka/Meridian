'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { useApiClient } from '@/hooks/useApiClient';
import Navigation from '@/components/Navigation';
import Link from 'next/link';

interface Project {
  id: string;
  name: string;
  description?: string;
  jobCount: number;
  lastActivityAt?: string;
  isArchived: boolean;
}

export default function DashboardPage() {
  const router = useRouter();
  const { isAuthenticated, user } = useAuth();
  const apiClient = useApiClient();

  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newProjectName, setNewProjectName] = useState('');
  const [newProjectDesc, setNewProjectDesc] = useState('');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Redirect if not authenticated
  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login');
    }
  }, [isAuthenticated, router]);

  // Load projects
  useEffect(() => {
    const loadProjects = async () => {
      if (!isAuthenticated) return;

      setLoading(true);
      setError(null);

      try {
        const response = await apiClient.post(
          '/graphql',
          {
            query: `
              query GetProjects {
                projects {
                  id
                  name
                  description
                  jobCount
                  lastActivityAt
                  isArchived
                }
              }
            `,
          }
        );

        if (response.data?.projects) {
          setProjects(response.data.projects.filter((p: Project) => !p.isArchived));
        }
      } catch (err) {
        console.error('Failed to load projects:', err);
        setError('Failed to load projects');
      } finally {
        setLoading(false);
      }
    };

    loadProjects();
  }, [isAuthenticated, apiClient]);

  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!newProjectName.trim()) {
      setError('Project name is required');
      return;
    }

    if (newProjectName.length > 100) {
      setError('Project name must be less than 100 characters');
      return;
    }

    setCreating(true);

    try {
      const response = await apiClient.post(
        '/graphql',
        {
          query: `
            mutation CreateProject($input: CreateProjectInput!) {
              createProject(input: $input) {
                id
                name
                description
              }
            }
          `,
          variables: {
            input: {
              name: newProjectName,
              description: newProjectDesc || null,
            },
          },
        }
      );

      if (response.data?.createProject?.id) {
        const newProject: Project = {
          id: response.data.createProject.id,
          name: response.data.createProject.name,
          description: response.data.createProject.description,
          jobCount: 0,
          lastActivityAt: new Date().toISOString(),
          isArchived: false,
        };
        setProjects([newProject, ...projects]);
        setShowCreateModal(false);
        setNewProjectName('');
        setNewProjectDesc('');
      }
    } catch (err: any) {
      console.error('Create project error:', err);
      setError(err?.message || 'Failed to create project');
    } finally {
      setCreating(false);
    }
  };

  const formatDate = (dateString?: string) => {
    if (!dateString) return 'No activity';
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  if (!isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white">
      <Navigation />

      <main className="max-w-6xl mx-auto px-4 py-12">
        {/* Header */}
        <div className="flex items-center justify-between mb-12">
          <div>
            <h1 className="text-4xl font-bold">Dashboard</h1>
            <p className="text-slate-400 mt-2">Welcome back, {user?.email}</p>
          </div>
          <button
            onClick={() => setShowCreateModal(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-3 px-6 rounded-lg transition"
          >
            + New Project
          </button>
        </div>

        {/* Error message */}
        {error && (
          <div className="bg-red-900 border border-red-700 rounded-lg p-4 mb-6">
            <p className="text-red-200">{error}</p>
          </div>
        )}

        {/* Projects Grid */}
        <div>
          {loading ? (
            <div className="text-center py-12">
              <div className="inline-block">
                <div className="flex gap-1">
                  <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce"></div>
                  <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
                  <div className="w-3 h-3 bg-blue-500 rounded-full animate-bounce" style={{ animationDelay: '0.4s' }}></div>
                </div>
              </div>
            </div>
          ) : projects.length === 0 ? (
            <div className="text-center py-12 bg-slate-900 rounded-lg border border-slate-700">
              <p className="text-slate-400 mb-4">No projects yet</p>
              <button
                onClick={() => setShowCreateModal(true)}
                className="inline-block bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-lg transition"
              >
                Create your first project
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {projects.map((project) => (
                <Link
                  key={project.id}
                  href={`/projects/${project.id}`}
                  className="block bg-slate-900 hover:bg-slate-800 border border-slate-700 rounded-lg p-6 transition"
                >
                  <h3 className="text-lg font-semibold mb-2 line-clamp-2">{project.name}</h3>
                  {project.description && (
                    <p className="text-slate-400 text-sm mb-4 line-clamp-2">{project.description}</p>
                  )}
                  <div className="flex items-center justify-between text-xs text-slate-400">
                    <span>📊 {project.jobCount} research job{project.jobCount !== 1 ? 's' : ''}</span>
                    <span>⏰ {formatDate(project.lastActivityAt)}</span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>
      </main>

      {/* Create Project Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-slate-900 border border-slate-700 rounded-lg p-6 w-full max-w-md mx-4">
            <h2 className="text-2xl font-bold mb-4">New Project</h2>
            <form onSubmit={handleCreateProject} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-2">Project Name *</label>
                <input
                  type="text"
                  value={newProjectName}
                  onChange={(e) => setNewProjectName(e.target.value)}
                  maxLength={100}
                  placeholder="e.g., Climate Change Research"
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-2">Description (Optional)</label>
                <textarea
                  value={newProjectDesc}
                  onChange={(e) => setNewProjectDesc(e.target.value)}
                  maxLength={500}
                  rows={3}
                  placeholder="Add a description..."
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                />
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="submit"
                  disabled={creating}
                  className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-700 text-white font-medium py-2 px-4 rounded-lg transition"
                >
                  {creating ? 'Creating...' : 'Create'}
                </button>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="flex-1 bg-slate-800 hover:bg-slate-700 text-white font-medium py-2 px-4 rounded-lg transition"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
