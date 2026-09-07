/**
 * PollPulse - Real-time Voting & Poll Client
 * Connects to Spring Boot backend via REST and Server-Sent Events (SSE).
 * Enforces strict single-vote per user per poll.
 */

const API_BASE = '/api/polls';

// State
let currentPollId = null;
let currentPollData = null;
let sseSource = null;
let isVoting = false;

// DOM Elements
const connectionStatusEl = document.getElementById('connection-status');
const pollDropdownEl = document.getElementById('poll-dropdown');
const loadingStateEl = document.getElementById('loading-state');
const pollCardEl = document.getElementById('poll-card');
const pollQuestionEl = document.getElementById('poll-question');
const pollDateEl = document.getElementById('poll-date');
const totalVotesCountEl = document.getElementById('total-votes-count');
const userVotedBadgeEl = document.getElementById('user-voted-badge');
const optionsContainerEl = document.getElementById('options-container');

// Modal Elements
const btnOpenCreateModal = document.getElementById('btn-open-create-modal');
const btnCloseModal = document.getElementById('btn-close-modal');
const btnCancelModal = document.getElementById('btn-cancel-modal');
const createModal = document.getElementById('create-modal');
const createPollForm = document.getElementById('create-poll-form');
const btnAddOption = document.getElementById('btn-add-option');
const modalOptionsList = document.getElementById('modal-options-list');
const toastContainer = document.getElementById('toast-container');

// Unique Voter ID generation and persistence
function getOrCreateVoterId() {
    let voterId = localStorage.getItem('pollpulse_voter_id');
    if (!voterId) {
        if (typeof crypto !== 'undefined' && crypto.randomUUID) {
            voterId = crypto.randomUUID();
        } else {
            voterId = 'voter_' + Date.now() + '_' + Math.random().toString(36).substring(2, 11);
        }
        localStorage.setItem('pollpulse_voter_id', voterId);
    }
    return voterId;
}

// Initialize Application
document.addEventListener('DOMContentLoaded', () => {
    getOrCreateVoterId();
    setupEventListeners();
    fetchPollsList();
});

function setupEventListeners() {
    // Poll dropdown change
    pollDropdownEl.addEventListener('change', (e) => {
        const selectedId = Number(e.target.value);
        if (selectedId && selectedId !== currentPollId) {
            selectPoll(selectedId);
        }
    });

    // Create Modal triggers
    btnOpenCreateModal.addEventListener('click', () => {
        createModal.classList.remove('hidden');
    });

    const closeModal = () => {
        createModal.classList.add('hidden');
        createPollForm.reset();
        resetModalOptions();
    };

    btnCloseModal.addEventListener('click', closeModal);
    btnCancelModal.addEventListener('click', closeModal);

    createModal.addEventListener('click', (e) => {
        if (e.target === createModal) closeModal();
    });

    // Add dynamic option row in modal
    btnAddOption.addEventListener('click', () => {
        const optionRows = modalOptionsList.querySelectorAll('.option-input-row');
        if (optionRows.length >= 10) {
            showToast('Maximum 10 options allowed per poll.', 'error');
            return;
        }

        const newRow = document.createElement('div');
        newRow.className = 'option-input-row';
        newRow.innerHTML = `
            <input type="text" class="modal-opt-input" placeholder="Option ${optionRows.length + 1}" required maxlength="255">
            <button type="button" class="btn-remove-opt" title="Remove option">×</button>
        `;

        newRow.querySelector('.btn-remove-opt').addEventListener('click', () => {
            newRow.remove();
        });

        modalOptionsList.appendChild(newRow);
        newRow.querySelector('input').focus();
    });

    // Handle initial remove button in modal
    modalOptionsList.querySelectorAll('.btn-remove-opt').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.target.closest('.option-input-row').remove();
        });
    });

    // Submit new poll form
    createPollForm.addEventListener('submit', handleCreatePoll);
}

function resetModalOptions() {
    modalOptionsList.innerHTML = `
        <div class="option-input-row">
            <input type="text" class="modal-opt-input" placeholder="Option 1" required maxlength="255">
        </div>
        <div class="option-input-row">
            <input type="text" class="modal-opt-input" placeholder="Option 2" required maxlength="255">
        </div>
        <div class="option-input-row">
            <input type="text" class="modal-opt-input" placeholder="Option 3 (optional)" maxlength="255">
            <button type="button" class="btn-remove-opt" title="Remove option">×</button>
        </div>
    `;
    modalOptionsList.querySelectorAll('.btn-remove-opt').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.target.closest('.option-input-row').remove();
        });
    });
}

// Fetch list of all polls
async function fetchPollsList(selectNewPollId = null) {
    try {
        const response = await fetch(API_BASE);
        if (!response.ok) throw new Error('Failed to load polls');
        const polls = await response.json();

        if (polls.length === 0) {
            loadingStateEl.innerHTML = '<p>No polls found. Click "+ New Poll" to create one!</p>';
            return;
        }

        // Populate dropdown
        pollDropdownEl.innerHTML = '';
        polls.forEach(poll => {
            const opt = document.createElement('option');
            opt.value = poll.id;
            opt.textContent = `${poll.id}. ${poll.question}`;
            pollDropdownEl.appendChild(opt);
        });

        // Determine which poll to display
        const targetId = selectNewPollId || (currentPollId ? currentPollId : polls[0].id);
        pollDropdownEl.value = targetId;
        selectPoll(targetId);

    } catch (err) {
        console.error('Error fetching polls:', err);
        showToast('Could not connect to server. Check if Spring Boot is running.', 'error');
    }
}

// Select and load a single poll
function selectPoll(pollId) {
    currentPollId = pollId;

    // Show loading skeleton if first time
    if (!currentPollData || currentPollData.id !== pollId) {
        loadingStateEl.classList.remove('hidden');
        pollCardEl.classList.add('hidden');
    }

    // Connect real-time Server-Sent Events (SSE)
    connectSse(pollId);

    // Sync vote status from backend for this voter
    const voterId = getOrCreateVoterId();
    fetch(`${API_BASE}/${pollId}/vote-status?voterId=${encodeURIComponent(voterId)}`)
        .then(res => res.ok ? res.json() : null)
        .then(status => {
            if (status && status.hasVoted && status.optionId) {
                saveUserVote(pollId, status.optionId);
            }
        })
        .catch(err => console.debug('Status check error:', err));

    // Fetch initial REST snapshot
    fetch(`${API_BASE}/${pollId}`)
        .then(res => {
            if (!res.ok) throw new Error('Poll not found');
            return res.json();
        })
        .then(data => {
            renderPoll(data);
        })
        .catch(err => {
            console.error('Error loading poll:', err);
            showToast('Failed to load poll details.', 'error');
        });
}

// Server-Sent Events (SSE) Stream Subscription
function connectSse(pollId) {
    if (sseSource) {
        sseSource.close();
        sseSource = null;
    }

    updateConnectionStatus('connecting', 'Connecting...');

    sseSource = new EventSource(`${API_BASE}/${pollId}/stream`);

    sseSource.onopen = () => {
        updateConnectionStatus('connected', 'Live Sync Active');
    };

    sseSource.addEventListener('poll-update', (event) => {
        try {
            const data = JSON.parse(event.data);
            if (data && data.id === currentPollId) {
                renderPoll(data);
            }
        } catch (e) {
            console.error('Error parsing SSE event:', e);
        }
    });

    sseSource.onerror = (err) => {
        updateConnectionStatus('disconnected', 'Reconnecting...');
        // EventSource will automatically attempt reconnection
    };
}

function updateConnectionStatus(state, text) {
    connectionStatusEl.className = 'status-indicator ' + state;
    const textSpan = connectionStatusEl.querySelector('.status-text');
    if (textSpan) textSpan.textContent = text;
}

// Render Poll Card & Options with Progress Bars
function renderPoll(poll) {
    currentPollData = poll;

    loadingStateEl.classList.add('hidden');
    pollCardEl.classList.remove('hidden');

    pollQuestionEl.textContent = poll.question;
    
    // Format created date
    if (poll.createdAt) {
        const date = new Date(poll.createdAt);
        pollDateEl.textContent = date.toLocaleDateString(undefined, {
            month: 'short',
            day: 'numeric',
            year: 'numeric'
        });
    }

    // Total votes with pulse effect
    const prevVotes = parseInt(totalVotesCountEl.textContent, 10) || 0;
    totalVotesCountEl.textContent = poll.totalVotes.toLocaleString();
    if (poll.totalVotes !== prevVotes) {
        totalVotesCountEl.classList.remove('pulse');
        void totalVotesCountEl.offsetWidth; // Trigger reflow
        totalVotesCountEl.classList.add('pulse');
    }

    // Check if user already voted in this poll (from localStorage)
    const userVotedOptionId = getUserVotedOption(poll.id);
    const hasVoted = userVotedOptionId !== null;

    if (hasVoted) {
        userVotedBadgeEl.classList.remove('hidden');
    } else {
        userVotedBadgeEl.classList.add('hidden');
    }

    // Update footer hint text
    const hintEl = document.querySelector('.poll-hint span:last-child');
    if (hintEl) {
        if (hasVoted) {
            hintEl.textContent = 'You have already voted in this poll. Live results are synced in real-time.';
        } else {
            hintEl.textContent = 'Click an option to cast your vote (1 vote per user). Live updates sync automatically.';
        }
    }

    // Find the leading option (highest percentage > 0)
    let maxVotes = -1;
    poll.options.forEach(opt => {
        if (opt.voteCount > maxVotes && opt.voteCount > 0) {
            maxVotes = opt.voteCount;
        }
    });

    // Render Options
    optionsContainerEl.innerHTML = '';
    poll.options.forEach((opt) => {
        const isSelected = hasVoted && (userVotedOptionId === opt.id);
        const isLeader = maxVotes > 0 && opt.voteCount === maxVotes;
        const isOther = hasVoted && !isSelected;

        const optionCard = document.createElement('div');
        optionCard.className = `option-item ${isSelected ? 'selected' : ''} ${isLeader ? 'leader' : ''} ${hasVoted ? 'disabled' : ''} ${isOther ? 'voted-other' : ''}`;
        optionCard.setAttribute('role', hasVoted ? 'status' : 'button');
        optionCard.setAttribute('tabindex', hasVoted ? '-1' : '0');
        optionCard.setAttribute('data-option-id', opt.id);

        optionCard.innerHTML = `
            <!-- Large background progress fill -->
            <div class="progress-fill" style="width: ${opt.percentage}%"></div>
            
            <div class="option-content">
                <div class="option-left">
                    <div class="vote-radio"></div>
                    <span class="option-text">
                        ${escapeHtml(opt.text)}
                        ${isSelected ? '<span class="leader-badge" style="background: rgba(99, 102, 241, 0.2); color: #818CF8; border-color: rgba(99, 102, 241, 0.4);">✓ Your Vote</span>' : ''}
                        ${isLeader ? '<span class="leader-badge">★ Leader</span>' : ''}
                    </span>
                </div>
                
                <div class="option-stats">
                    <span class="vote-tally">${opt.voteCount} ${opt.voteCount === 1 ? 'vote' : 'votes'}</span>
                    <span class="vote-percentage">${opt.percentage.toFixed(1)}%</span>
                </div>
            </div>

            <!-- Mini crisp progress bar -->
            <div class="progress-track-wrapper">
                <div class="progress-track-bar" style="width: ${opt.percentage}%"></div>
            </div>
        `;

        if (!hasVoted) {
            // Click to Vote
            optionCard.addEventListener('click', () => {
                castVote(poll.id, opt.id, opt.text);
            });

            // Keyboard navigation (Enter / Space to vote)
            optionCard.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    castVote(poll.id, opt.id, opt.text);
                }
            });
        } else {
            // Inform user if they try to click again
            optionCard.addEventListener('click', () => {
                showToast('You have already voted in this poll.', 'info');
            });
        }

        optionsContainerEl.appendChild(optionCard);
    });
}

// Cast Vote Action
async function castVote(pollId, optionId, optionText) {
    if (isVoting) return;

    if (getUserVotedOption(pollId) !== null) {
        showToast('You have already voted in this poll.', 'error');
        return;
    }

    isVoting = true;
    const voterId = getOrCreateVoterId();

    try {
        const response = await fetch(`${API_BASE}/${pollId}/vote`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                optionId: optionId,
                voterId: voterId
            })
        });

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            const errMsg = errData.message || 'Failed to submit vote';
            
            // If already voted (409 Conflict), record locally and lock UI
            if (response.status === 409) {
                saveUserVote(pollId, optionId);
                if (currentPollData) {
                    renderPoll(currentPollData);
                }
            }
            throw new Error(errMsg);
        }

        const updatedPoll = await response.json();

        // Save vote locally
        saveUserVote(pollId, optionId);

        // Instant update
        renderPoll(updatedPoll);

        showToast(`Vote recorded for "${optionText}"!`, 'success');

    } catch (err) {
        console.error('Error voting:', err);
        showToast(err.message || 'Could not register vote. Try again.', 'error');
    } finally {
        isVoting = false;
    }
}

// Create New Poll Action
async function handleCreatePoll(e) {
    e.preventDefault();

    const questionInput = document.getElementById('new-question');
    const question = questionInput.value.trim();

    const optionInputs = modalOptionsList.querySelectorAll('.modal-opt-input');
    const options = [];
    optionInputs.forEach(input => {
        const val = input.value.trim();
        if (val) options.push(val);
    });

    if (!question) {
        showToast('Please enter a poll question', 'error');
        return;
    }

    if (options.length < 2) {
        showToast('Please provide at least 2 options', 'error');
        return;
    }

    const submitBtn = document.getElementById('btn-submit-poll');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Publishing...';

    try {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ question, options })
        });

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.message || 'Failed to create poll');
        }

        const newPoll = await response.json();

        // Close modal and clear form
        createModal.classList.add('hidden');
        createPollForm.reset();
        resetModalOptions();

        showToast('New poll created successfully!', 'success');

        // Reload poll list and switch to the new poll
        await fetchPollsList(newPoll.id);

    } catch (err) {
        console.error('Error creating poll:', err);
        showToast(err.message || 'Could not create poll.', 'error');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Publish Poll';
    }
}

// Local Storage helpers for remembering user votes
function getUserVotedOption(pollId) {
    const val = localStorage.getItem(`voted_poll_${pollId}`);
    return val ? Number(val) : null;
}

function saveUserVote(pollId, optionId) {
    localStorage.setItem(`voted_poll_${pollId}`, optionId);
}

// Toast Notification
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '⚠️';

    toast.innerHTML = `
        <span class="toast-icon">${icon}</span>
        <span class="toast-message">${escapeHtml(message)}</span>
    `;

    toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

// Helper to escape HTML characters
function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>'"]/g, 
        tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag] || tag)
    );
}
