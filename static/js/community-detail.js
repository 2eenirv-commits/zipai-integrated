(async function () {
  'use strict';
  const auth = window.ZipaiAuth;
  if (auth) await auth.ready;
  const user = auth && auth.getUser();
  const gate = document.getElementById('memberGate');
  const section = document.getElementById('postDetailSection');
  if (!user) { gate.hidden = false; section.hidden = true; return; }
  const params = new URLSearchParams(window.location.search);
  const id = Number(params.get('id'));
  const source = params.get('source') || 'sample';
  const toast = document.getElementById('communityToast');
  async function api(path, options) {
    const response = await fetch(path, { credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, ...options });
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(payload.message || '게시글을 불러오지 못했습니다.');
    return payload;
  }
  function showToast(message) {
    toast.textContent = message; toast.classList.add('is-visible');
    setTimeout(function () { toast.classList.remove('is-visible'); }, 2000);
  }
  function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, function (char) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char];
    });
  }
  function formatDate(value, withTime) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '날짜 정보 없음';
    return new Intl.DateTimeFormat('ko-KR', withTime
      ? { dateStyle: 'medium', timeStyle: 'short' }
      : { dateStyle: 'long' }).format(date);
  }
  let post;
  if (source === 'server') {
    try { post = (await api('/api/community/posts/' + id)).item; }
    catch (error) { showToast(error.message); setTimeout(function () { location.replace('/board/community'); }, 900); return; }
  } else post = (window.ZipaiCommunitySamples || []).find(function (item) { return item.id === id; });
  if (!post) return location.replace('/board/community');
  const labels = { review: '주거 후기', tip: '생활 정보', question: '질문', free: '자유게시판' };
  gate.hidden = true; section.hidden = false;
  const category = document.getElementById('detailCategory');
  category.textContent = labels[post.category] || '게시글';
  category.classList.add(labels[post.category] ? post.category : 'free');
  document.getElementById('detailTitle').textContent = post.title;
  document.getElementById('detailAuthor').textContent = post.author;
  document.getElementById('detailDate').textContent = formatDate(post.createdAt, false);
  document.getElementById('detailArea').textContent = post.area || '';
  document.getElementById('detailArea').hidden = !post.area;
  document.getElementById('detailRating').textContent = post.category === 'review' ? '★'.repeat(Number(post.rating || 0)) + '☆'.repeat(5 - Number(post.rating || 0)) : '';
  const detailImage = document.getElementById('detailImage');
  if (post.imageUrl) { detailImage.src = post.imageUrl; detailImage.hidden = false; }
  document.getElementById('detailContent').textContent = post.content;
  document.getElementById('detailLikes').textContent = post.likes || 0;
  document.getElementById('detailViews').textContent = post.views || 0;
  const likeButton = document.getElementById('detailLike');
  likeButton.classList.toggle('is-liked', Boolean(post.liked));
  likeButton.disabled = source !== 'server' || Boolean(post.liked);
  likeButton.addEventListener('click', async function () {
    if (post.liked || source !== 'server') return;
    try {
      const payload = await api('/api/community/posts/' + id + '/like', { method: 'PUT', body: '{}' });
      post.liked = true; document.getElementById('detailLikes').textContent = payload.likes;
      likeButton.classList.add('is-liked'); likeButton.disabled = true; showToast('게시글에 공감했습니다.');
    } catch (error) { showToast(error.message); }
  });
  const reportButton = document.getElementById('detailReport');
  reportButton.disabled = source !== 'server';
  reportButton.addEventListener('click', async function () {
    if (source !== 'server') return;
    const reason = window.prompt('신고 사유를 입력해 주세요.', '부적절한 내용');
    if (reason === null) return;
    try {
      const payload = await api('/api/community/posts/' + id + '/report', { method: 'POST', body: JSON.stringify({ reason: reason }) });
      reportButton.disabled = true;
      showToast(payload.isBlinded ? '신고가 접수되어 게시글이 블라인드 처리되었습니다.' : '신고가 접수되었습니다.');
    } catch (error) { showToast(error.message); }
  });
  const commentsSection = document.getElementById('commentSection');
  if (source !== 'server') {
    commentsSection.innerHTML = '<p class="activity-empty">예시 게시글에는 댓글을 작성할 수 없습니다.</p>';
    return;
  }
  const commentList = document.getElementById('commentList');
  const commentForm = document.getElementById('commentForm');
  function renderComments(items) {
    document.getElementById('commentCount').textContent = items.length;
    commentList.innerHTML = items.length ? items.map(function (item) {
      return '<article class="community-comment"><strong>' + escapeHtml(item.author) + '</strong><p>' + escapeHtml(item.content) +
        '</p><time>' + formatDate(item.createdAt, true) +
        '</time></article>';
    }).join('') : '<p class="activity-empty">첫 댓글을 남겨보세요.</p>';
  }
  let comments;
  try { comments = (await api('/api/community/posts/' + id + '/comments')).items || []; }
  catch (error) { comments = []; showToast(error.message); }
  renderComments(comments);
  commentForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    const content = commentForm.elements.content.value.trim();
    if (!content) return;
    try {
      const payload = await api('/api/community/posts/' + id + '/comments', { method: 'POST', body: JSON.stringify({ content: content }) });
      comments.push(payload.item); commentForm.reset(); renderComments(comments); showToast('댓글이 등록되었습니다.');
    } catch (error) { showToast(error.message); }
  });
})();
