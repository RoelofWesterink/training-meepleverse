const title = document.querySelector('#title');
const message = document.querySelector('#message');
const next = document.querySelector('#next');
const success = document.querySelector('#success');
const retry = document.querySelector('#retry');
const confetti = document.querySelector('#confetti');
let cleanup;

function celebrate() {
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  clearTimeout(cleanup);
  confetti.replaceChildren();
  const colors = ['#c2e4fb', '#6dafdf', '#ffffff', '#e9bb67', '#b7a4ed'];
  for (let i = 0; i < 140; i++) {
    const piece = document.createElement('i');
    piece.className = 'confetto';
    piece.style.left = `${Math.random() * 100}%`;
    piece.style.background = colors[i % colors.length];
    piece.style.setProperty('--duration', `${3 + Math.random() * 3}s`);
    piece.style.setProperty('--delay', `${Math.random() * 1.2}s`);
    piece.style.setProperty('--drift', `${Math.random() * 200 - 100}px`);
    piece.style.setProperty('--rotation', `${Math.random() * 1080 - 540}deg`);
    confetti.append(piece);
  }
  cleanup = setTimeout(() => confetti.replaceChildren(), 7500);
}

async function check() {
  retry.hidden = true;
  title.textContent = 'One moment…';
  message.textContent = 'Checking your development environment.';
  next.textContent = 'We’re asking the Java service to say hello.';
  try {
    const response = await fetch('/api/status', { cache: 'no-store', signal: AbortSignal.timeout(10000) });
    const greeting = await response.text();
    if (!response.ok || greeting.trim() !== 'Your Fluxzero development environment is working.') {
      throw new Error('The service did not return the expected greeting.');
    }
    document.body.classList.add('ready');
    title.textContent = 'You’re ready!';
    message.textContent = greeting;
    next.hidden = true;
    success.hidden = false;
    celebrate();
  } catch {
    title.textContent = 'Not quite yet…';
    message.textContent = 'The page is here, but the service has not answered yet.';
    next.textContent = 'Check Workspace in Devboard for startup or build errors, then try again.';
    retry.hidden = false;
  }
}

document.querySelector('#celebrate').addEventListener('click', celebrate);
retry.addEventListener('click', check);
check();
