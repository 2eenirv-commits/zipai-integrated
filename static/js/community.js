(async function () {
  'use strict';
  const auth = window.ZipaiAuth;
  if (auth) await auth.ready;
  const user = auth && auth.getUser();
  const gate = document.getElementById('memberGate');
  const workspace = document.getElementById('communityWorkspace');
  if (!user) { gate.hidden = false; workspace.hidden = true; return; }

  const samples = window.ZipaiCommunitySamples || [];
  let posts = [];
  let selectedCategory = 'all';
  let query = '';
  let sort = 'latest';
  const form = document.getElementById('communityWriteForm');
  const list = document.getElementById('communityPostList');
  const toast = document.getElementById('communityToast');
  gate.hidden = true;
  workspace.hidden = false;
  document.getElementById('communityUser').textContent = user.id;

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, function (char) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char];
    });
  }
  async function api(path, options) {
    const response = await fetch(path, { credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, ...options });
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(payload.message || '게시판 요청을 처리하지 못했습니다.');
    return payload;
  }
  async function uploadImage(file) {
    if (!file || !file.size) return '';
    const formData = new FormData();
    formData.append('file', file);
    const response = await fetch('/api/community/posts/upload', { method: 'POST', credentials: 'same-origin', body: formData });
    const payload = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(payload.message || '이미지를 업로드하지 못했습니다.');
    return payload.url || '';
  }
  function label(value) {
    return ({ review: '주거 후기', tip: '생활 정보', question: '질문', free: '자유게시판' })[value] || '게시글';
  }
  function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '날짜 정보 없음';
    return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
  }
  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    setTimeout(function () { toast.classList.remove('is-visible'); }, 2200);
  }
  function allPosts() {
    return posts.concat(samples.map(function (post) { return { ...post, source: 'sample' }; }));
  }
  function render() {
    const keyword = query.trim().toLowerCase();
    const filtered = allPosts().filter(function (post) {
      return (selectedCategory === 'all' || post.category === selectedCategory) &&
        (!keyword || (post.title + ' ' + post.content + ' ' + post.author).toLowerCase().includes(keyword));
    }).sort(function (a, b) {
      return sort === 'likes' ? b.likes - a.likes : new Date(b.createdAt) - new Date(a.createdAt);
    });
    document.getElementById('postCount').textContent = filtered.length;
    list.innerHTML = filtered.length ? filtered.map(function (post) {
      const rating = post.category === 'review' ? '<span class="post-rating">' + '★'.repeat(Number(post.rating || 0)) + '☆'.repeat(5 - Number(post.rating || 0)) + '</span>' : '';
      const date = formatDate(post.createdAt);
      const image = post.imageUrl ? '<img class="community-post-thumb" src="' + escapeHtml(post.imageUrl) + '" alt="">' : '';
      return '<article class="community-post" data-post-id="' + post.id + '" data-source="' + (post.source || 'server') + '" role="link" tabindex="0">' +
        '<span class="post-category ' + escapeHtml(post.category) + '">' + label(post.category) + '</span><div class="post-main">' + image + '<h3>' +
        escapeHtml(post.title) + '</h3><p>' + escapeHtml(post.content) + '</p><div class="post-meta"><span>' +
        escapeHtml(post.author) + '</span><span>' + date + '</span>' + rating + (post.area ? '<span>' + escapeHtml(post.area) + '</span>' : '') +
        '</div></div><div class="post-stats"><button type="button" data-like="' + post.id + '"><i class="' +
        (post.liked ? 'fa-solid' : 'fa-regular') + ' fa-heart"></i> ' + post.likes + '</button>' +
        (post.commentCount ? '<span><i class="fa-regular fa-comment"></i> ' + post.commentCount + '</span>' : '') +
        '<span><i class="fa-regular fa-eye"></i> ' + post.views + '</span></div></article>';
    }).join('') : '<div class="board-empty"><i class="fa-regular fa-message"></i><p>조건에 맞는 게시글이 없습니다.</p></div>';
  }
  function setWriteOpen(open) { form.hidden = !open; if (open) form.elements.title.focus(); }
  function updateReviewFields() { document.getElementById('reviewFields').hidden = form.elements.category.value !== 'review'; }

  document.getElementById('openWriteForm').addEventListener('click', function () { setWriteOpen(true); });
  document.getElementById('closeWriteForm').addEventListener('click', function () { setWriteOpen(false); });
  document.getElementById('cancelWrite').addEventListener('click', function () { setWriteOpen(false); });
  form.elements.category.addEventListener('change', updateReviewFields);
  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (!form.checkValidity()) return form.reportValidity();
    const data = new FormData(form);
    try {
      const imageUrl = await uploadImage(data.get('image'));
      const payload = await api('/api/community/posts', { method: 'POST', body: JSON.stringify({
        category: data.get('category'), title: data.get('title'), content: data.get('content'),
        area: data.get('area'), rating: Number(data.get('rating') || 0), imageUrl: imageUrl
      }) });
      posts.unshift({ ...payload.item, source: 'server' });
      form.reset(); updateReviewFields(); setWriteOpen(false); render();
      showToast('게시글이 등록되었습니다.');
    } catch (error) { showToast(error.message); }
  });
  document.querySelectorAll('[data-category]').forEach(function (button) {
    button.addEventListener('click', function () {
      selectedCategory = button.dataset.category;
      document.querySelectorAll('[data-category]').forEach(function (item) { item.classList.toggle('active', item === button); });
      render();
    });
  });
  document.getElementById('boardSearch').addEventListener('input', function (event) { query = event.target.value; render(); });
  document.getElementById('postSort').addEventListener('change', function (event) { sort = event.target.value; render(); });
  list.addEventListener('click', async function (event) {
    const article = event.target.closest('[data-post-id]');
    if (!article) return;
    const button = event.target.closest('[data-like]');
    if (!button) {
      window.location.href = '/board/community-detail?id=' + encodeURIComponent(article.dataset.postId) + '&source=' + article.dataset.source;
      return;
    }
    if (article.dataset.source === 'sample') return showToast('예시 글은 상세 화면에서 확인해 주세요.');
    const post = posts.find(function (item) { return item.id === Number(article.dataset.postId); });
    if (!post || post.liked) return showToast('이미 공감한 글입니다.');
    try {
      const payload = await api('/api/community/posts/' + post.id + '/like', { method: 'PUT', body: '{}' });
      post.likes = payload.likes; post.liked = true; render(); showToast('게시글에 공감했습니다.');
    } catch (error) { showToast(error.message); }
  });
  list.addEventListener('keydown', function (event) {
    if ((event.key !== 'Enter' && event.key !== ' ') || event.target.closest('[data-like]')) return;
    const article = event.target.closest('[data-post-id]');
    if (!article) return;
    event.preventDefault();
    window.location.href = '/board/community-detail?id=' + encodeURIComponent(article.dataset.postId) + '&source=' + article.dataset.source;
  });

  updateReviewFields();
  try {
    posts = (await api('/api/community/posts')).items.map(function (post) { return { ...post, source: 'server' }; });
  } catch (error) { showToast(error.message); }
  render();
})();
