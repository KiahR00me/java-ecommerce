package com.java.ecommerce.category;

import com.java.ecommerce.common.BusinessException;
import com.java.ecommerce.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    @Transactional
    public Category create(String name, String description) {
        categoryRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new BusinessException("Category already exists: " + name);
        });
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(Long id, String name, String description) {
        Category category = findById(id);
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException("Category already exists: " + name);
                });
        category.setName(name);
        category.setDescription(description);
        return category;
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.delete(findById(id));
    }
}
